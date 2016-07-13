import heapq
import requests as req
import json
import math
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import pywt
import scipy as sp
from scipy import fftpack
from scipy import interpolate
from scipy import signal
from scipy import stats
from sortedcontainers import SortedDict as sd
from collections import Counter

ROOT = 'http://localhost:8080/argusws'
with open('.login.txt') as login:
    username = login.readline().rstrip()
    password = login.readline().rstrip()
login_info = json.dumps({'username':username, 'password':password})
headers = {'content-type':'application/json'}

auth = req.post(ROOT + '/auth/login', data=login_info, headers=headers)
cookies = auth.cookies

r = req.get(ROOT + '/metrics?expression=-5d:argus.jvm:mem.heap.used:avg', headers=headers, cookies=cookies)
data = sd(r.json()[0]['datapoints'])

keys = [int(key) for key in data.keys()]
diff_counts = Counter([keys[i+1] - keys[i] for i in range(len(keys)-1)])
values = [float(data[key]) for key in sorted(data.keys())]

def plot_original_data(keys, values):
    plt.plot(keys, values)
    plt.title('Original Data')
    plt.show()
plot_original_data(keys, values)

def get_data(filename):
    df = pd.read_csv(filename, skipfooter=3)
    data = df.iloc[:-1, 1]
    data = [float(num) for num in data]
    times = [i for i in range(len(data))]
    return times, data

def most_common_diff(lst):
    diffs = [lst[i+1] - lst[i] for i in range(len(lst) - 1)]
    return Counter(diffs).most_common(1)[0][1]


def fill_interpolate(time, values):
    f = interpolate.interp1d(time, values)
    norm_times = [i for i in range(time[0], time[-1], most_common_diff(time))]
    print(Counter([norm_times[i+1] - norm_times[i] for i in range(len(norm_times) - 1)]))
    return norm_times, f(norm_times)

def wavelet_anomaly(time, values, wavelet='db2'):
    cA, cD = pywt.dwt(values, wavelet)
    approx = pywt.idwt(cA, np.zeros((len(cD,))), wavelet)
    if len(values) != len(approx):
        approx = approx[:-1]
    
    resid = values - approx

#     plt.plot(time, approx)
#     plt.title('Approximation')
#     plt.show()

    plt.plot(time, resid)
    plt.title(wavelet + ' Residuals')
    plt.show()
    return resid

def gaussian_pdf(resid):
    mean = np.mean(resid)
    std = np.std(resid)
    gaussian = (1 - stats.norm.pdf((resid - [mean]*len(resid))/std)/stats.norm.pdf(0))
    return gaussian


def symmetric_anomaly_graph(filename):
    times, data = get_data(filename)
    plot_original_data(times, data)
    resid = wavelet_anomaly(times, data)
    gauss = gaussian_pdf(resid)
    plt.plot(times, gauss)
    plt.title('Gaussian Values')
    plt.show()
    return times, resid

def anomaly_graph(times, data):
    resid = wavelet_anomaly(times, data)
    gauss = gaussian_pdf(resid)
    plt.plot(times, gauss)
    plt.title('Gaussian Values')
    plt.show()
    return times, resid

def fft_components(time_series_dict):
    keys = sorted(time_series_dict.keys())
    values = [time_series_dict[key] for key in keys]
    components = np.fft.rfft(values)
    return components

def fft_components(times, values):
    components = np.fft.rfft(values)
    return components

