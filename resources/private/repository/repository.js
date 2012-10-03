/*
	repository.js:
*/

var repository = function() {

	var applications = undefined;
	var applicationsById = undefined;
	
	function loadApplications(data) {
		debug("reloading successful");
		applications = data.applications;
		applicationsById = {};
		for (var n = 0; n < applications.length; n++) {
			var application = applications[n];
			var id = application.id;
			applicationsById[id] = application;
			hub.send("repository-application-reload", {applicationId: id});
		}
		hub.send("repository-reload");
	}
	
	function reloadAllApplications(callback) {
		debug("reloading started");
		applications = undefined;
		applicationsById = undefined;
		ajax.query("applications")
			.success(function(d) {
				loadApplications(d);
				if (callback) callback(d);
			})
			.call();
	}

	hub.subscribe("login", function() { reloadAllApplications(); });

	hub.subscribe("logout", function(e) {
		applications = undefined;
		applicationsById = undefined;
		hub.send("repository-reload");
	});

	function getApplications(callback, error) {
		if (applications) {
			callback(applications);
		} else {
			hub.subscribe("repository-reload", function() { callback(applications); }, true);
		}
	}
	
	function getApplication(id, callback, error) {
		if (applicationsById) {
			var app = applicationsById[id];
			if (app) {
				callback(app);
			} else {
				error();
			}
		} else {
			hub.subscribe("repository-reload", function() {
				var app = applicationsById[id];
				if (app) {
					callback(app);
				} else {
					error();
				}
			}, true);
		}
	}
	
	return {
		getApplications:       getApplications,
		getApplication:        getApplication,
		reloadAllApplications: reloadAllApplications
	};
	
}();
