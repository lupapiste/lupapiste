LUPAPISTE.OperationEditorModel = function(params) {
  "use strict";
  var self = this;

  self.docModel = params.docModel;
  self.docId = self.docModel.docId;
  self.operation = params.operation;
  self.isPrimaryOperation = params.isPrimary;
  var auth = params.auth;

  // Operation description.
  self.description = ko.observable(self.operation.description()).extend({rateLimit: {timeout: 500, method: "notifyWhenChangesStop"}});
  self.enabled = self.operation && auth.ok( "update-op-description");

  self.description.subscribe( function(desc) {
    hub.send("accordionService::saveOperationDescription", {appId: self.docModel.appId,
                                                            operationId: self.operation.id(),
                                                            description: desc});
  });

  // Star
  self.showStar = auth.ok( "change-primary-operation");
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

  self.dispose = function() {
    // save operation description state on dispose, if timeout hasn't triggered observable
    hub.send("accordionService::saveOperationDescription", {appId: self.docModel.appId,
                                                            operationId: self.operation.id(),
                                                            description: self.description()});
  };

};
