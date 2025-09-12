# Nifty 50 Live Driver Analysis

This Python script provides a real-time analysis of the stocks driving the Nifty 50 index. It uses the Angel One Smart API to fetch live stock data and identifies which stocks are the top contributors to the index's movement, both in terms of points and percentage change.

## Features

- **Real-time Data:** Connects to the Angel One WebSocket for live stock ticks.
- **Nifty 50 Drivers:** Calculates the point-wise and percentage-wise contribution of each stock to the Nifty 50's movement.
- **Continuous Monitoring:** Runs in a continuous loop, refreshing the data every 10 seconds.
- **Sortable Views:** Automatically cycles through different sort orders to show:
    - Top positive point drivers
    - Top negative point drivers
    - Top positive percentage gainers
    - Top negative percentage losers
- **Dynamic Data Fetching:** Automatically fetches the latest Nifty 50 constituents, instrument tokens, and stock weights from online sources.

## Prerequisites

- Python 3.6+
- An active Angel One trading account.
- API credentials from the Angel One Smart API portal.

## Setup Instructions

### 1. Install Dependencies

This script requires the following Python libraries. You can install them using pip:

```bash
pip install smartapi-python requests beautifulsoup4 pandas pyotp
```

### 2. Configure Your Credentials

The script requires your Angel One API credentials.

1.  **Create `config.py`:**
    In the same directory as the `nifty_driver.py` script, create a new file named `config.py`.

2.  **Add Your Credentials:**
    Copy the following content into `config.py` and replace the placeholder values with your actual credentials:

    ```python
    # Angel One Smart API credentials
    API_KEY = "YOUR_API_KEY"
    CLIENT_CODE = "YOUR_CLIENT_CODE"
    PIN = "YOUR_PIN"

    # This is the secret key from your TOTP authenticator app (e.g., Google Authenticator)
    # It is NOT the 6-digit code. It's the long string you get when you set up TOTP.
    TOTP_TOKEN = "YOUR_TOTP_SECRET_KEY"
    ```

    **Important:** The `TOTP_TOKEN` is your TOTP secret key (a long alphanumeric string), not the 6-digit number that changes every 30 seconds. The script uses this secret key to generate the 6-digit code automatically.

### 3. (Optional) Update Nifty's Previous Day Close

For a more accurate point contribution calculation, you can update the `NIFTY_PREV_CLOSE` variable in `nifty_driver.py` with the actual previous day's closing value of the Nifty 50 index.

```python
# in nifty_driver.py
NIFTY_PREV_CLOSE = 22000.00 # <-- Change this value
```

## How to Run the Script

Once you have installed the dependencies and configured your credentials, you can run the script from your terminal:

```bash
python nifty_driver.py
```

The script will start, authenticate with the API, and begin displaying the live Nifty 50 driver analysis. The table will refresh and re-sort automatically every 10 seconds.

To stop the script, press `Ctrl+C`.
