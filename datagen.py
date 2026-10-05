# =============================================================================
# CELL 1 — DATA INGESTION
# Bot A (strong) plays Bot B (weaker). Only Bot A's moves from games that
# Bot A WINS are kept and written to a CSV.
# =============================================================================

# ------------------------------- FLAGS ---------------------------------------
NUM_GAMES   = 100000   # number of games Bot A must WIN (losses / draws are discarded and replayed)
BOT_A_LEVEL = 12   # Bot A strength = Stockfish search depth (higher = stronger, slower)
BOT_B_LEVEL = 8     # Bot B strength = Stockfish search depth (keep it below Bot A)
NUM_WORKERS = 10  # games played in parallel (one thread + 2 Stockfish processes each);
                    # None = one per CPU core. Stockfish is CPU-bound, so a GPU does not help here.
ENGINE_THREADS = 1  # search threads inside each Stockfish process (CPU load ~ NUM_WORKERS x ENGINE_THREADS)

# ---------------------------- optional tuning --------------------------------
RANDOM_OPENING_PLIES = 4     # random legal moves before the bots take over -> different start every game
MAX_GAME_PLIES = 300         # abandon (and replay) games that drag on longer than this
OUTPUT_CSV     = "bot_a_wins_dataset.csv"
APPEND_TO_CSV  = True       # True = add the new games to the existing CSV instead of overwriting it
# -----------------------------------------------------------------------------

import os, sys, shutil, subprocess, random, threading, time


def _sh(cmd):
    subprocess.run(cmd, shell=True, check=True)


# --- dependencies (Stockfish + python-chess) ---------------------------------
if shutil.which("stockfish") is None and not os.path.exists("/usr/games/stockfish"):
    _sh("apt-get update -qq && apt-get install -y -qq stockfish")
try:
    import chess
except ImportError:
    _sh(f"{sys.executable} -m pip install -q chess")
    import chess
import chess.engine
import pandas as pd

STOCKFISH = shutil.which("stockfish") or "/usr/games/stockfish"
NUM_WORKERS = max(1, NUM_WORKERS or 2)
if BOT_A_LEVEL <= BOT_B_LEVEL:
    print("WARNING: BOT_A_LEVEL should be higher than BOT_B_LEVEL, otherwise Bot A "
          "rarely wins and collecting games will take very long.")


def move_to_class(move, black_to_move):
    """Class index = fromSquare * 64 + toSquare (0..4095).
    For Black both squares are mirrored with (63 - square), exactly like the Java app
    does (see WEIGHTS_CONTEXT.md), so the label matches the mirrored board encoding."""
    frm, to = move.from_square, move.to_square
    if black_to_move:
        frm, to = 63 - frm, 63 - to
    return frm * 64 + to


def play_game(engine_a, engine_b, stop):
    """Play one game. Returns Bot A's move records if Bot A won, otherwise None."""
    board = chess.Board()
    for _ in range(RANDOM_OPENING_PLIES):            # entropy: random, unrecorded opening
        if board.is_game_over():
            return None
        board.push(random.choice(list(board.legal_moves)))
    if board.is_game_over():
        return None

    bot_a_color = random.choice([chess.WHITE, chess.BLACK])
    token = object()                                  # tells Stockfish "this is a new game"
    records = []                                      # Bot A moves only
    while not board.is_game_over(claim_draw=True):
        if stop.is_set() or len(board.move_stack) >= MAX_GAME_PLIES:
            return None
        if board.turn == bot_a_color:
            move = engine_a.play(board, chess.engine.Limit(depth=BOT_A_LEVEL), game=token).move
            records.append({
                "ply_number": len(board.move_stack),
                "fen": board.fen(),
                "target_move_uci": move.uci(),
                "target_move_idx": move_to_class(move, board.turn == chess.BLACK),
            })
        else:                                         # Bot B moves are played but never recorded
            move = engine_b.play(board, chess.engine.Limit(depth=BOT_B_LEVEL), game=token).move
        board.push(move)

    outcome = board.outcome(claim_draw=True)
    return records if outcome is not None and outcome.winner == bot_a_color else None


games, errors = [], []                                # games = list of per-game record lists
stats = {"played": 0}
lock, stop = threading.Lock(), threading.Event()


def worker():
    try:
        # two engine processes per worker, reused for every game this worker plays
        with chess.engine.SimpleEngine.popen_uci(STOCKFISH) as eng_a, \
             chess.engine.SimpleEngine.popen_uci(STOCKFISH) as eng_b:
            for eng in (eng_a, eng_b):
                eng.configure({"Threads": ENGINE_THREADS, "Hash": 16})
            while not stop.is_set():
                records = play_game(eng_a, eng_b, stop)
                with lock:
                    if stop.is_set():
                        break
                    stats["played"] += 1
                    if records:
                        games.append(records)
                        n = len(games)
                        if n % 25 == 0 or n == NUM_GAMES:
                            print(f"  {n}/{NUM_GAMES} Bot A wins collected "
                                  f"({stats['played']} games played, {time.time() - t0:,.0f}s)")
                        if n >= NUM_GAMES:
                            stop.set()
    except Exception as exc:                          # e.g. Stockfish could not be started
        errors.append(exc)
        stop.set()


print(f"Collecting {NUM_GAMES} Bot A wins | Bot A depth {BOT_A_LEVEL} vs Bot B depth {BOT_B_LEVEL} "
      f"| {NUM_WORKERS} parallel workers x {ENGINE_THREADS} engine thread(s) "
      f"| {os.cpu_count()} CPU cores available")
t0 = time.time()
threads = [threading.Thread(target=worker, daemon=True) for _ in range(NUM_WORKERS)]
for t in threads:
    t.start()
try:
    while any(t.is_alive() for t in threads):
        time.sleep(0.5)
except KeyboardInterrupt:
    print("Interrupted - stopping workers and saving the games collected so far...")
    stop.set()
for t in threads:
    t.join()

if errors:
    raise errors[0]
if not games:
    raise RuntimeError("No winning games were collected.")

rows = [dict(rec, game_id=gid) for gid, g in enumerate(games, start=1) for rec in g]
df_dataset = pd.DataFrame(rows)[["game_id", "ply_number", "fen", "target_move_uci", "target_move_idx"]]
n_new = len(df_dataset)
if APPEND_TO_CSV and os.path.exists(OUTPUT_CSV):
    old_df = pd.read_csv(OUTPUT_CSV)
    df_dataset["game_id"] += old_df["game_id"].max()          # keep game ids unique across runs
    df_dataset = pd.concat([old_df, df_dataset], ignore_index=True)
    print(f"Appending to existing {OUTPUT_CSV} ({len(old_df):,} positions already there)")
df_dataset.to_csv(OUTPUT_CSV, index=False)
print(f"Collected {n_new:,} Bot A positions from {len(games)} won games "
      f"({stats['played']} games played, {time.time() - t0:,.0f}s). "
      f"{OUTPUT_CSV} now holds {len(df_dataset):,} positions.")