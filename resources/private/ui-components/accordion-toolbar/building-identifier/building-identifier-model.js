LUPAPISTE.BuildingIdentifierModel = function(params) {
  "use strict";
  var self = this;

  self.docModel = params.docModel;
  self.operation = params.operation;
  self.isPrimaryOperation = params.isPrimary;
  self.authModel = params.authModel;
  self.options = params.options;

  self.documentId = self.docModel.docId;
  self.service = lupapisteApp.services.accordionService;
  self.identifier = ko.observable(self.service.getBuildingId(self.documentId)).extend({rateLimit: {timeout: 500, method: "notifyWhenChangesStop"}});
  self.indicator = ko.observable();

  var subscription = self.identifier.subscribe( function(buildingId) {
    hub.send("accordionService::saveBuildingId", {docId: self.documentId, value: buildingId, indicator: self.indicator});
  });


  self.dispose = function() {
    subscription.dispose();
    hub.send("accordionService::saveBuildingId", {docId: self.documentId, value: self.identifier(), indicator: self.indicator});
  };

  self.enabled = ko.pureComputed(function() {
    return self.options.disabled === false || self.authModel.ok("update-doc");
  });

  // Star
  self.showStar = self.authModel.ok( "change-primary-operation");
  self.starTitle = ko.pureComputed( function() {
    return self.isPrimaryOperation()
      ? "operations.primary"
      : "operations.primary.select";
  });
  self.clickStar = function() {
    ajax.command("change-primary-operation",
      {id: self.docModel.appId,
        secondaryOperationId: self.operation.id()})
      .success(function() {
        repository.load(self.docModel.appId);
      })
      .call();
    return false;
  };

};
