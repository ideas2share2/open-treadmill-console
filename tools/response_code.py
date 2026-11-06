#!/usr/bin/env python3
"""Offline legacy owner-service response calculator. No network, files or device access."""
import argparse
import re

MULTIPLIER = 0x5DEECE66D
MASK = (1 << 48) - 1
BOUND = 999999


def parse_challenge(value):
    if not isinstance(value, str) or re.fullmatch(r'[0-9]{1,6}', value) is None:
        raise ValueError('Enter the 1–6 ASCII digits shown by your own legacy console.')
    return int(value, 10)


def calculate(challenge, mode='java'):
    if mode not in ('java', 'helper'):
        raise ValueError('Mode must be java or helper.')
    state = (parse_challenge(challenge) ^ MULTIPLIER) & MASK
    while True:
        state = (state * MULTIPLIER + 11) & MASK
        bits = state >> 17
        result = bits % BOUND
        # Java nextInt rejects signed-32-bit overflow; the community JS helper does not.
        if mode == 'helper' or bits - result + BOUND - 1 < (1 << 31):
            return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('challenge', help='Current challenge from your own legacy console')
    parser.add_argument('--mode', choices=('java', 'helper'), default='java',
                        help='java: exact java.util.Random behavior; helper: match community JS helper')
    args = parser.parse_args()
    try:
        print(calculate(args.challenge, args.mode))
    except ValueError as error:
        parser.error(str(error))


if __name__ == '__main__':
    main()
