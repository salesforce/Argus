ArgusClient
=========
ArgusClient is a command line distributed execution client used to perform asynchronous operations.  Such operations include:
* Evaluating alerts
* Committing metric and event data into persistent storage.

Typical Argus deployments will have many clients running to perform these functions.  The actual number of clients dedicated for each operation depends on the scale of the deployment.  Small deployments may only require one or two clients of each type in order to fulfill the asynchronous execution needs of Argus, where large scale deployments may run fifty, one hundred or more.

To find out more [see the wiki.](https://github.com/SalesforceEng/Argus/wiki)
