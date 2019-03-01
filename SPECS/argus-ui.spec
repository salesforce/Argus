#
# Argus web services
#
BuildArch:     noarch
Name:          argusui
Version:       4.70
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
mkdir -p %{_stagerootdir}/%{_tmpdir}
cp -r %{_topdir}/ArgusWeb/app/*	 %{_stagerootdir}/%{_tmpdir}

%files
%defattr(755, sfdc, sfdc,755)
%{_tmpdir}

%pre

%post
if [ ! -d "%{_targetbasedir}" ]
then
  mkdir -p %{_targetbasedir}
  chown -R sfdc:sfdc %{_targetbasedir}
fi
if [ -d "%{_targetdir}" ]
then
  rm -rf %{_targetdir}
fi
cp -r %{_tmpdir} %{_targetdir}
chown -R sfdc:sfdc %{_targetdir}
echo "Installation complete."
