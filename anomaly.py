import requests as req
import json
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import pywt
import scipy as sp
from scipy import fftpack
from scipy import interpolate
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


def get_data(filename):
    df = pd.read_csv(filename)
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

def wavelet_anomaly(time, values):
    cA, cD = pywt.dwt(values, 'db2')
    approx = pywt.idwt(cA, np.zeros((len(cD,))), 'db2')
    if len(values) == len(approx):
        resid = values - approx
    else:
        resid = values - approx[:-1]
    plt.plot(time, resid)
    plt.title('Residuals')
    plt.show()
    return resid

def gaussian_pdf(resid):
    mean = np.mean(resid)
    std = np.std(resid)
    gaussian = stats.norm.pdf(resid, mean, std)
    return gaussian

def symmetric_anomaly_graph(filename):
    times, data = get_data(filename)
    resid = wavelet_anomaly(times, data)
    gauss = gaussian_pdf(resid)
    plt.plot(times, gauss)
    plt.title('Gaussian Values')
    plt.show()
    return gauss

def fft_components(time_series_dict):
    keys = sorted(time_series_dict.keys())
    values = [time_series_dict[key] for key in keys]
    components = np.fft.rfft(values)
    return components

def fft_components(times, values):
    components = np.fft.rfft(values)
    return components



