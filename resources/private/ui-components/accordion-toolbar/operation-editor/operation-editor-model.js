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
  self.showDescription = self.operation && auth.ok( "update-op-description");

  var descriptionSub = self.description.subscribe( function( desc ) {
    ajax.command ("update-op-description", {id: self.docModel.appId,
                                            "op-id": self.operation.id(),
                                            desc: desc  })
    .success (function(resp) {
      hub.send("op-description-changed", {appId: self.docModel.appId,
                                          "op-id": self.operation.id(),
                                          "op-desc": desc  });
      util.showSavedIndicator(resp);
    })
    .call();
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
    descriptionSub.dispose();
  };

};
