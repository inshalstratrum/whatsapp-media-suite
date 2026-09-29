"""Entry point: python -m wmsuite -> GUI by default, or a subcommand."""

from __future__ import annotations

import sys


def main():
    if len(sys.argv) > 1 and sys.argv[1] != "gui":
        from .cli import main as cli_main

        return cli_main(sys.argv[1:])
    from .gui import run

    return run()


if __name__ == "__main__":
    raise SystemExit(main())
