// Service for fetching and sharing building information.
LUPAPISTE.BuildingService = function() {
  "use strict";
  var self = this;

  var buildings = ko.observableArray();

  self.buildingsAttributes = ko.observableArray();
  self.archiveUpdatePending = ko.observable(false);
  self.allowedForOrg = ko.observable(false);
  self.initialFetchPending = ko.observable(true);

  var latestPropertyId = null;

  function appId() {
    return lupapisteApp.services.contextService.applicationId();
  }

  function updateNeeded() {
    if( appId() ) {
      var propertyId = lupapisteApp.models.application.propertyId();
      if( propertyId !== latestPropertyId ) {
        latestPropertyId = propertyId;
        return true;
      }
    } else {
      // We are not on an application page.
      latestPropertyId = null;
    }
  }

  // Shared computed (with every DocgenBuildingSelectModel) that
  // provides a "read-only" view of the fetched building information.
  var infoView = ko.computed( function() {
    return _.clone( buildings() );
  });

  self.buildingsInfo = function() {
    if( updateNeeded() && lupapisteApp.models.applicationAuthModel.ok("get-building-info-from-wfs")) {
      ajax.query( "get-building-info-from-wfs",
                  {id: appId()})
          .success( function( res ) {
            buildings( res.data );
          })
          .error(util.showSavedIndicator)
          .call();
    }
    // Returns immediately with the view observable.
    return infoView;
  };

  // Options [optional]
  // documentId: Document id
  // buildingId: Building id
  // overwrite: Whether overwrite during merge
  // [path]: Schema path (default "buildingId")
  // [collection]: Mongo collection ("documents")
  hub.subscribe( "buildingService::merge", function( options ) {
    ajax.command("merge-details-from-krysp",
                 _.defaults( _.pick( options, ["documentId",
                                               "buildingId",
                                               "overwrite"]),
                             {id: appId()}))
     .success( _.partial( repository.load, appId(), _.noop))
      .onError("error.no-legacy-available", notify.ajaxError)
      .call();
  });


  self.isEmpty = function(val) {
      return !_.isNumber(val) && _.isEmpty(val);
  };

  self.emptyToUndefined = function(val) {
      return !_.isBoolean(val) && self.isEmpty(val) ? undefined : val;
  };

  self.emptyToNull = function(val) {
      return !_.isBoolean(val) && self.isEmpty(val) ? null : val;
  };

  self.toBuildingField = function(value, transform) {
      transform = transform || _.identity;
      value = transform(self.emptyToUndefined(value));
      return {value: ko.observable(value), error: ko.observable(null)};
  };


  hub.subscribe("buildingService::fetchBuildings", function(options) {
      var organizationId = _.get(options, "organizationId");
      if (organizationId) {
          ajax.query("buildings",
                     {organizationId: organizationId})
              .success(function(res) {
                  self.allowedForOrg(true);
                  self.initialFetchPending(false);
                  var buildingRows = _.map(res.data, function(building) {
                      return {
                          "id":               self.toBuildingField(building.id),
                          "ratu":             self.toBuildingField(building.ratu),
                          "vtjprt":           self.toBuildingField(building.vtjprt),
                          "kiinteistotunnus": self.toBuildingField(building.kiinteistotunnus, util.prop.toHumanFormat),
                          "visibility":       self.toBuildingField(building.visibility),
                          "publicity":        self.toBuildingField(building.publicity),
                          "myyntipalvelussa": self.toBuildingField(building.myyntipalvelussa),
                          "address":          self.toBuildingField(building.address),
                          "comment":          self.toBuildingField(building.comment),
                          "modified":         self.toBuildingField(building.modified),
                          "sentToArchive":    self.toBuildingField(building["sent-to-archive"]),
                          "unsaved":          ko.observable(false)
                      };
                  });
                  self.buildingsAttributes(buildingRows);
              })
              .error(function (err) {
                  self.initialFetchPending(false);
                  if (err.text === "error.unsupported-permit-type") {
                      self.allowedForOrg(false);
                  } else {
                      util.showSavedIndicator(err);
                  }
              })
              .call();
      }
  });

  self.newRow = function() {
      return {
          "id":               self.toBuildingField(null),
          "ratu":             self.toBuildingField(null),
          "vtjprt":           self.toBuildingField(null),
          "kiinteistotunnus": self.toBuildingField(null),
          "visibility":       self.toBuildingField(null),
          "publicity":        self.toBuildingField(null),
          "myyntipalvelussa": self.toBuildingField(null),
          "address":          self.toBuildingField(null),
          "comment":          self.toBuildingField(null),
          "modified":         self.toBuildingField(null),
          "sentToArchive":    self.toBuildingField(null),
          "unsaved":          ko.observable(true)
      };
  };

  hub.subscribe("buildingService::addNewBuildingRow", function(options) {
      self.buildingsAttributes.push(self.newRow());
      _.invoke(options, "callback");
  });

  self.findBuilding = function(buildingId) {
      return _.find(self.buildingsAttributes(), function(building) {
          return buildingId === building.id.value();
      });
  };

  self.showSaveError = function() {
      util.showSavedIndicator({"ok": false, "text": "error.building.update-failed"});
  };

  self.getUnsavedBuilding = function() {
    var unsavedBuildings =_.filter(self.buildingsAttributes(), function(building) {
        return building.unsaved();
    });
    if (unsavedBuildings.length > 1 || unsavedBuildings.length === 0 ) {
        self.showSaveError();
        return null;
    }
    return _.first(unsavedBuildings);
  };

  self.transformValue = function(field, value, extraHandlers) {
      var transformers = {
          "kiinteistotunnus": [_.trim, util.prop.toDbFormat],
          "vtjprt": [_.trim],
          "ratu": [_.trim]
      };
      var transform = _.get(transformers, field, _.identity);
      var preHandlers = _.get(extraHandlers, "pre", []);
      var postHandlers = _.get(extraHandlers, "post", []);
      var handler = _.flow(_.concat(preHandlers, transform, postHandlers));
      var transformedValue = value;
      try {
          transformedValue = handler(value);
      }
      catch(err) {
          // Invalid value for transformer. Backend will report the error
      }
      return transformedValue;
  };

  self.updateValues = function(buildingOnUI, updatedBuilding) {
      if (updatedBuilding && updatedBuilding.modified) {
          buildingOnUI.modified.value(updatedBuilding.modified);
      }
  };

  hub.subscribe("buildingService::saveBuildingField", function( options ) {
      var value = self.transformValue(options.fieldName, options.fieldValue, {"post": [self.emptyToNull]});
      var params = ko.toJS({organizationId: options.organizationId,
                            "building-update": {
                                "id": options.id,
                                "field": options.fieldName,
                                "value": value}
                           });
      ajax.command("update-building", params)
          .success( function( res ) {
              var buildingId = res.data["building-id"];
              var building = self.findBuilding(buildingId);
              var updatedBuilding = res.data.building;
              if (!building) {
                  var unsavedBuilding = self.getUnsavedBuilding();
                  if (unsavedBuilding) {
                      unsavedBuilding.id.value(buildingId);
                      unsavedBuilding.unsaved(false);
                      unsavedBuilding[options.fieldName].error(null);
                      self.updateValues(unsavedBuilding, updatedBuilding);
                  }
              } else {
                  self.updateValues(building, updatedBuilding);
                  building[options.fieldName].error(null);
              }
          })
          .error(function (err) {
              var building = self.findBuilding(options.id);
              building[options.fieldName].error(err.text);
              if (err.text !== "error.building.invalid-attribute") {
                  util.showSavedIndicator(err);
              }
          })
          .call();
  });

  self.removeFromTable = function (buildingId) {
      self.buildingsAttributes.remove(function(building) {
          return building.id.value() === buildingId;
      });
  };

  hub.subscribe("buildingService::removeBuildingRow", function( options ) {
      var params = ko.toJS({organizationId: options.organizationId,
                            "building-id": options.buildingId});
      var building = self.findBuilding(options.buildingId);
      if (building.unsaved()) {
          self.removeFromTable(options.buildingId);
      } else {
          ajax.command("remove-building", params)
              .success( function( ) {
                  self.removeFromTable(options.buildingId);
              })
              .error(util.showSavedIndicator)
              .call();
      }
  });

    self.updateSentToArchiveFields = function(updateResults) {
        _.forEach(updateResults, function(updateResult, buildingId) {
            var building = _.find(self.buildingsAttributes(), function(building) {
                return building.id.value() === buildingId;
            });
            building.sentToArchive.value(updateResult);
        });
    };


  hub.subscribe("buildingService::updateInArchive", function( options ) {
      self.archiveUpdatePending(true);
      var params = ko.toJS({organizationId: options.organizationId,
                            "building-ids": options.buildingIds});
      ajax.command("update-buildings-in-archive", params)
          .success( function(response) {
              self.updateSentToArchiveFields(response.data);
              options.onResults(response.data);
              self.archiveUpdatePending(false);
          })
          .error(function(e) {
              if (e.data) {
                  options.onResults(e.data);
              }
              util.showSavedIndicator(e);
              self.archiveUpdatePending(false);
          })
          .onTimeout(function() {
              util.showSavedIndicator({"ok": false, "text": "error.building.archive-update-timeout"});
              self.archiveUpdatePending(false);
          })
          .call();
  });

};
