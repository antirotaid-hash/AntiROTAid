"""Put the engine_ara root on sys.path so `import mindlift` resolves the lean
AntiROTAid engine (not any other copy)."""
import sys
from pathlib import Path

ENGINE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ENGINE_ROOT))
