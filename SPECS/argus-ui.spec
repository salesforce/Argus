#
# Argus web services
#
BuildArch:     noarch
Name:          argusui
Version:       4.46
Release:       1
License:       BSD-3-Clause
Group:         Applications/System
Summary:       Argus web services
Distribution:  Salesforce.com, Inc.
Vendor:        Salesforce.com, Inc.
Packager:      Salesforce.com, Inc.
URL:           https://github.com/SalesforceEng/Argus

%define _stagerootdir	%{_buildrootdir}/%{name}-%{version}-%{release}.noarch

%description
Argus is a time-series monitoring and alerting platform. Argus UI provides visualization.

%prep
rm -rf %{_stagerootdir}
mkdir -p %{_stagerootdir}

%install
pwd
mkdir -p %{_stagerootdir}/%{_targetdir}
cp -r %{_topdir}/ArgusWeb/app/*	 %{_stagerootdir}/%{_targetdir}

%files
%defattr(755, sfdc, sfdc,755)
%{_targetdir}/../../../..

%pre

%post
echo "Installation complete."
