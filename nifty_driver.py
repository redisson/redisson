# nifty_driver.py
#
# This script fetches real-time data for Nifty 50 stocks using the Angel One Smart API,
# calculates their contribution to the Nifty 50 index's movement, and displays
# the results in a continuously updating, sortable table.
#
# Author: Jules
# Date: 2025-09-12

import requests
from bs4 import BeautifulSoup
import json
import pyotp
from SmartApi import SmartConnect
from SmartApi.smartWebSocketV2 import SmartWebSocketV2
import time
import os
import pandas as pd
import threading

# --- Configuration ---
# The script expects a 'config.py' file in the same directory with the following content:
# API_KEY = "YOUR_API_KEY"
# CLIENT_CODE = "YOUR_CLIENT_CODE"
# PIN = "YOUR_PIN"
# TOTP_TOKEN = "YOUR_TOTP_TOKEN_SECRET" # The secret key from your authenticator app

try:
    import config
except ImportError:
    print("Error: config.py not found. Please create a config.py file with your credentials.")
    exit()

# --- Global variables ---
# This dictionary will store the real-time data for each stock.
# The key is the stock's token, and the value is another dictionary with its data.
stock_data = {}

# NIFTY_PREV_CLOSE is used to calculate the point contribution.
# For higher accuracy, this should be updated daily with the actual previous day's close of the Nifty 50 index.
NIFTY_PREV_CLOSE = 22000.00

# A lock to ensure thread-safe access to the shared stock_data dictionary.
lock = threading.Lock()

# --- Helper Functions for Data Fetching ---

def get_nifty50_stocks():
    """
    Fetches the list of Nifty 50 stock symbols from the dhan.co website.
    This is done by scraping the page for image URLs that contain the stock symbols.
    Returns a list of stock symbols (e.g., ['RELIANCE-EQ', 'HDFCBANK-EQ']).
    """
    print("Fetching Nifty 50 constituents...")
    url = "https://dhan.co/nifty-stocks-list/nifty-50/"
    try:
        response = requests.get(url, timeout=10)
        response.raise_for_status()
        soup = BeautifulSoup(response.text, 'html.parser')
        symbols = set()
        for img in soup.find_all('img', src=True):
            if 'images.dhan.co/symbol/' in img['src']:
                symbol = img['src'].split('/')[-1].replace('.png', '')
                if "&" not in symbol:  # Filter out invalid symbols
                    symbols.add(f"{symbol}-EQ")
        print(f"Found {len(symbols)} stocks from dhan.co.")
        return list(symbols)
    except requests.exceptions.RequestException as e:
        print(f"Error fetching Nifty 50 stocks: {e}")
        return []

def get_instrument_tokens():
    """
    Fetches the complete list of tradable instruments from Angel One's API.
    It then creates a mapping from a stock symbol to its unique instrument token.
    Returns a dictionary (e.g., {'RELIANCE-EQ': '2885'}).
    """
    print("Fetching instrument tokens...")
    url = "https://margincalculator.angelbroking.com/OpenAPI_File/files/OpenAPIScripMaster.json"
    try:
        response = requests.get(url, timeout=10)
        response.raise_for_status()
        instruments = response.json()
        token_map = {}
        for instrument in instruments:
            if instrument.get('exch_seg') == 'NSE' and instrument.get('instrumenttype') == 'EQ':
                symbol = instrument.get('symbol', '')
                token = instrument.get('token')
                if symbol and token:
                    token_map[f"{symbol.split('-')[0]}-EQ"] = token
        print(f"Created token map for {len(token_map)} NSE equity symbols.")
        return token_map
    except (requests.exceptions.RequestException, json.JSONDecodeError) as e:
        print(f"Error fetching or parsing instrument tokens: {e}")
        return {}

def get_nifty50_weights():
    """
    Fetches the weightage of each stock in the Nifty 50 index from a third-party website.
    Returns a dictionary mapping stock symbols to their weightage percentage.
    """
    print("Fetching Nifty 50 weights...")
    url = "https://www.smart-investing.in/indices-bse-nse.php?index=NIFTY"
    try:
        response = requests.get(url, timeout=10)
        response.raise_for_status()
        soup = BeautifulSoup(response.text, 'html.parser')
        weights = {}
        table = soup.find('table', {'class': 'table table-bordered table-striped table-hover'})
        if table:
            for row in table.find_all('tr')[1:]:
                cols = row.find_all('td')
                if len(cols) >= 3:
                    symbol_link = cols[1].find('a')
                    if symbol_link:
                        symbol = symbol_link.text.strip()
                        full_symbol = f"{symbol.upper()}-EQ"
                        weight_str = cols[2].text.strip()
                        weight = float(weight_str.replace('%', ''))
                        weights[full_symbol] = weight
        print(f"Found weights for {len(weights)} stocks.")
        return weights
    except (requests.exceptions.RequestException, ValueError) as e:
        print(f"Error fetching or parsing Nifty 50 weights: {e}")
        return {}

# --- WebSocket Event Handlers ---

def on_open(wsapp):
    """Called when the WebSocket connection is established."""
    print("WebSocket connection opened.")

def on_data(wsapp, message):
    """
    Called for each message received from the WebSocket.
    This function processes the live tick data and updates the global stock_data dictionary.
    """
    global stock_data, lock
    with lock:
        if 'token' in message and 'last_traded_price' in message:
            token = str(message['token'])
            if token in stock_data:
                ltp = message['last_traded_price'] / 100.0
                prev_close = stock_data[token].get('prev_close', ltp)
                if prev_close == 0: prev_close = ltp

                stock_data[token]['ltp'] = ltp
                stock_data[token]['change'] = ltp - prev_close
                stock_data[token]['percent_change'] = ((ltp - prev_close) / prev_close) * 100 if prev_close else 0

                weight = stock_data[token]['weight']
                point_contribution = (stock_data[token]['percent_change'] / 100) * (weight / 100) * NIFTY_PREV_CLOSE
                stock_data[token]['point_contribution'] = point_contribution

def on_error(wsapp, error):
    """Called when a WebSocket error occurs."""
    print(f"WebSocket Error: {error}")

def on_close(wsapp):
    """Called when the WebSocket connection is closed."""
    print("WebSocket connection closed.")

# --- Main Application Loop ---

def main_loop(wsapp):
    """
    The main loop of the application. It continuously clears the screen,
    displays the sorted stock data, and handles user input for sorting.
    """
    sort_by = 'point_contribution'
    sort_ascending = False

    while True:
        try:
            os.system('cls' if os.name == 'nt' else 'clear')

            with lock:
                if not stock_data:
                    print("Waiting for data...")
                    time.sleep(2)
                    continue

                df = pd.DataFrame.from_dict(stock_data, orient='index')

                if 'prev_close' not in df.columns or df['prev_close'].eq(0).any():
                    df['prev_close'] = df['ltp']
                    for token, row in df.iterrows():
                        if stock_data[token].get('prev_close', 0) == 0:
                            stock_data[token]['prev_close'] = row['ltp']

                df = df[df.ltp > 0]
                if df.empty:
                    print("Waiting for first ticks...")
                    time.sleep(2)
                    continue

                df = df.sort_values(by=sort_by, ascending=sort_ascending)

                print("--- Nifty 50 Drivers (Live) ---")
                print(f"Nifty Previous Day Close (for calculation): {NIFTY_PREV_CLOSE}")
                print(f"Sorted by: {sort_by} ({'Ascending' if sort_ascending else 'Descending'})")
                print(df[['symbol', 'ltp', 'change', 'percent_change', 'point_contribution']].round(2))

                total_point_contribution = df['point_contribution'].sum()
                print(f"\nTotal Nifty 50 Point Change Contribution: {total_point_contribution:.2f}")

                # Simple auto-cycling sort order
                print("\nSorting will cycle automatically every 10 seconds.")
                print("Press Ctrl+C to exit.")

            time.sleep(10)

            if sort_by == 'point_contribution' and not sort_ascending:
                sort_by, sort_ascending = 'point_contribution', True
            elif sort_by == 'point_contribution' and sort_ascending:
                sort_by, sort_ascending = 'percent_change', False
            elif sort_by == 'percent_change' and not sort_ascending:
                sort_by, sort_ascending = 'percent_change', True
            else:
                sort_by, sort_ascending = 'point_contribution', False

        except Exception as e:
            print(f"An error occurred in the main loop: {e}")
            time.sleep(5)


if __name__ == '__main__':
    print("--- Initializing Nifty 50 Driver Script ---")

    # --- Step 1: API Authentication ---
    print("\n--- Authenticating with Angle One Smart API ---")
    smartApi = SmartConnect(config.API_KEY)
    try:
        totp = pyotp.TOTP(config.TOTP_TOKEN).now()
    except Exception as e:
        print(f"Error generating TOTP: {e}. Please check your TOTP_TOKEN in config.py.")
        exit()
    data = smartApi.generateSession(config.CLIENT_CODE, config.PIN, totp)
    if not data.get('data'):
        print(f"Authentication failed: {data.get('message', 'Unknown error')}")
        exit()

    print("Authentication successful.")
    authToken = data['data']['jwtToken']
    feedToken = smartApi.getfeedToken()

    # --- Step 2: Fetch initial data ---
    print("\n--- Fetching required data ---")
    nifty50_stocks = get_nifty50_stocks()
    token_map = get_instrument_tokens()
    nifty50_weights = get_nifty50_weights()

    for symbol in nifty50_stocks:
        token = token_map.get(symbol)
        if token:
            stock_data[token] = {
                'symbol': symbol,
                'weight': nifty50_weights.get(symbol, 0),
                'ltp': 0, 'change': 0, 'percent_change': 0, 'point_contribution': 0
            }

    # --- Step 3: WebSocket Connection ---
    print("\n--- Connecting to WebSocket ---")
    sws = SmartWebSocketV2(authToken, config.API_KEY, config.CLIENT_CODE, feedToken)
    sws.on_open = on_open
    sws.on_data = on_data
    sws.on_error = on_error
    sws.on_close = on_close

    ws_thread = threading.Thread(target=sws.connect)
    ws_thread.daemon = True
    ws_thread.start()

    time.sleep(2) # Give time for connection

    token_list = [{"exchangeType": 1, "tokens": list(stock_data.keys())}]
    sws.subscribe("nifty50_driver", 1, token_list)

    # --- Step 4: Start Main Application Loop ---
    try:
        main_loop(sws)
    except KeyboardInterrupt:
        print("\nExiting...")
        sws.close_connection()
        time.sleep(1)
        exit()
