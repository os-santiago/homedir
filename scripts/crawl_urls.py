#!/usr/bin/env python3
"""
Crawl a local or remote website and list all internal URLs.
Example:
    python scripts/crawl_urls.py http://localhost:8080
"""

import sys
import requests
from urllib.parse import urljoin, urlparse
from bs4 import BeautifulSoup
from collections import deque

# ---- CONFIG ----
MAX_DEPTH = 3         # limita profundidad del recorrido
TIMEOUT = 5
HEADERS = {"User-Agent": "NaviaCrawler/0.1"}
# -----------------

def crawl(base_url):
    visited = set()
    queue = deque([(base_url, 0)])
    base_netloc = urlparse(base_url).netloc

    while queue:
        url, depth = queue.popleft()
        if url in visited or depth > MAX_DEPTH:
            continue
        visited.add(url)

        try:
            r = requests.get(url, headers=HEADERS, timeout=TIMEOUT)
            r.raise_for_status()
        except Exception as e:
            print(f"[WARN] {url} -> {e}")
            continue

        print(url)

        soup = BeautifulSoup(r.text, "html.parser")
        for link in soup.find_all("a", href=True):
            new_url = urljoin(url, link["href"])
            parsed = urlparse(new_url)

            # solo urls del mismo dominio / localhost
            if parsed.netloc != base_netloc and parsed.netloc not in ("", "localhost"):
                continue

            # normaliza (sin #fragmentos)
            clean = new_url.split("#")[0].rstrip("/")
            if clean not in visited:
                queue.append((clean, depth + 1))

    return visited


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python scripts/crawl_urls.py <base_url>")
        sys.exit(1)

    base_url = sys.argv[1].rstrip("/")
    urls = crawl(base_url)

    print("\n----- SUMMARY -----")
    print(f"Total URLs found: {len(urls)}")
    out_file = "urls.txt"
    with open(out_file, "w", encoding="utf-8") as f:
        for u in sorted(urls):
            f.write(u + "\n")
    print(f"Saved to {out_file}")
