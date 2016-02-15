LUPAPISTE.DocumentIdentifierModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;

  self.documentId = params.docId;
  self.authModel = params.authModel;
  self.options = params.options; // DocModel options (disabled setting)

  self.myService = lupapisteApp.services.accordionService;

  self.identifierObject = self.myService.getIdentifier(self.documentId);
  self.schema = self.identifierObject.schema;
  self.identifier = self.identifierObject &&
                    ko.observable(self.identifierObject.value()).extend({rateLimit: {timeout: 500, method: "notifyWhenChangesStop"}});

  if (ko.isObservable(self.identifier)) {
    self.identifier.subscribe(function(value) {
      hub.send("accordionService::saveIdentifier", {docId: self.documentId, key: self.identifierObject.key, value: value});
    });
  }

  self.enabled = ko.pureComputed(function() {
    return self.options.disabled === false || self.authModel.ok("update-doc");
  });

  self.readonly = ko.observable(false);
  self.inputOptions = {maxLength: self.schema["max-len"] || LUPAPISTE.config.inputMaxLength};

  self.dispose = function() {
    // save on dispose as subscription is not triggered if component is killed quicker than timeout
    hub.send("accordionService::saveIdentifier", {docId: self.documentId, key: self.identifierObject.key, value: self.identifier()});
  };

};
