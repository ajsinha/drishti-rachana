# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
# Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
"""``python -m drishti_rachana lint ...``: the ``rachana`` command."""
import sys

from .cli import main

sys.exit(main())
