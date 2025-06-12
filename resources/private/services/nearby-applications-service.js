// Service managing nearby application markers on a map.
LUPAPISTE.NearbyApplicationsService = function () {
  "use strict";
  var self = this;

  self.serviceName = "nearbyApplicationsService";
  self.nearbyApplications = ko.observableArray();
  var currentApplicationId = null;
  var currentApplicationLocation = null;

  self.nearbyApplicationMarkersVisible = ko.pureComputed(function () {
    return lupapisteApp.models.applicationAuthModel.ok("nearby-application-markers");
  });

  function location() {
    return ko.mapping.toJS(lupapisteApp.models.application.location());
  }

  function fetchNearbyApplications(appId, location) {
    currentApplicationId = appId;
    currentApplicationLocation = location;
    if (self.nearbyApplicationMarkersVisible()) {
      var idTitle = loc("applications.id.longtitle");
      var createdTitle = loc("common.created");
      var title = loc("application.address");
      var propertyTitle = loc("neighbors.edit.propertyId");
      var stateTitle = loc("company.report.excel.header.state");
      var operationTitle = loc("auth-admin.automatic-emails.primary-operation");
      ajax.query("nearby-application-markers", {id: appId})
        .success(function (data) {
          if (data) {
            self.nearbyApplications(_.map(data.nearbyApplications, function (app) {
              var appData = {};
              appData[idTitle] = app.id;
              appData[createdTitle] = util.finnishDate(app.created);
              appData[title] = app.address;
              appData[propertyTitle] = util.prop.toHumanFormat(app.propertyId);
              try {
                appData[operationTitle] = app.primaryOperation.name;
              } catch (_) {
                appData[operationTitle] = "";
              }
              appData[stateTitle] = loc(app.state);
              return {
                applicationData: appData,
                applicationId: app.id,
                applicationLocation: app["location-wgs84"],
                geometries: app.geometries
              };
            }));
          }
        })
        .call();
    }
  }

  hub.subscribe("application-model-updated", function () {
    if (currentApplicationId !== lupapisteApp.models.application.id() ||
      JSON.stringify(currentApplicationLocation) !== JSON.stringify(location())) {
      fetchNearbyApplications(lupapisteApp.models.application.id(), location());
    }
  });

};
