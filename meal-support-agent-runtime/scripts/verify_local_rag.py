"""Verify the local RAG index with a few probe queries.

Usage:
    python scripts/verify_local_rag.py
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.deps import get_rag_service  # noqa: E402

PROBES = ["排队要等多久", "优惠券怎么领取", "退款多久到账", "下单流程是什么"]


def main() -> None:
    service = get_rag_service()
    print(f"index size: {service._store.count}")
    for probe in PROBES:
        result = service.search(probe, top_k=2)
        print(f"\n== {probe} ==")
        for hit in result["results"]:
            print(f"  [{hit['source']}#{hit['chunkIndex']}] {hit['content'][:80]}...")


if __name__ == "__main__":
    main()
