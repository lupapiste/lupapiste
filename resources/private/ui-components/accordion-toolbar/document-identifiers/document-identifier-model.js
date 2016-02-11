LUPAPISTE.DocumentIdentifierModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;

  self.documentId = params.docId;

  self.myService = lupapisteApp.services.accordionService;

  self.identifierObject = self.myService.getIdentifier(self.documentId);
  self.identifier = self.identifierObject && self.identifierObject.value.extend({rateLimit: {timeout: 500, method: "notifyWhenChangesStop"}});

  if (ko.isObservable(self.identifier)) {
    self.identifier.subscribe(function(value) {
      hub.send("accordionService::saveIdentifier", {docId: self.documentId, key: self.identifierObject.key ,value: value});
    });
  }


};
