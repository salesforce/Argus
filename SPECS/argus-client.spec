#
# Argus alert client
#
BuildArch:     noarch
Name:          argusclient
Version:       4.73
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
Argus is a time-series monitoring and alerting platform. Argus client is used to evaluate alerts, commit metrics, annotations and schema.

%prep
rm -rf %{_stagerootdir}
mkdir -p %{_stagerootdir}

%install
pwd
mkdir -p %{_stagerootdir}/%{_tmpdir}
cp %{_topdir}/ArgusClient/target/argus-client-*-jar-with-dependencies.jar %{_stagerootdir}/%{_tmpdir}
wget --no-check-certificate https://+XcZR4PQ:u55T3x40oGiY2OxBV5Ykv9hEO+lFxbg+TzY68D39BEJp@nexus.soma.salesforce.com/nexus/content/groups/public/com/sfdc-bouncycastle/bc-fips/1.0.1/bc-fips-1.0.1.jar -O %{_stagerootdir}/%{_tmpdir}/bc-fips.jar
wget --no-check-certificate https://+XcZR4PQ:u55T3x40oGiY2OxBV5Ykv9hEO+lFxbg+TzY68D39BEJp@nexus.soma.salesforce.com/nexus/content/groups/public/com/sfdc-bouncycastle/bctls-fips/1.0.4/bctls-fips-1.0.4.jar -O %{_stagerootdir}/%{_tmpdir}/bctls-fips.jar

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
