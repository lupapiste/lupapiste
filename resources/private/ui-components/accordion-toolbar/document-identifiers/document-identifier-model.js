LUPAPISTE.DocumentIdentifierModel = function(params) {
  "use strict";
  var self = this;
  self.params = params;

  self.documentId = params.docId;
  self.authModel = params.authModel;
  self.options = params.options; // DocModel options (disabled setting)
  self.indicator = ko.observable();

  self.myService = lupapisteApp.services.accordionService;

  self.identifierObject = self.myService.getIdentifier(self.documentId);
  self.schema = self.identifierObject.schema;
  self.identifier = self.identifierObject &&
                    ko.observable(self.identifierObject.value()).extend({rateLimit: {timeout: 500, method: "notifyWhenChangesStop"}});

  var subscription = null;
  if (ko.isObservable(self.identifier)) {
    subscription = self.identifier.subscribe(function(value) {
      hub.send("accordionService::saveIdentifier", {docId: self.documentId, key: self.identifierObject.key, value: value, indicator: self.indicator});
    });
  }

  self.enabled = ko.pureComputed(function() {
    return self.options.disabled === false || self.authModel.ok("update-doc");
  });

  self.requiredHighlight = ko.pureComputed(function() {
    return self.schema.required && !self.identifier();
  });

  self.readonly = ko.observable(false);
  self.inputOptions = {maxLength: self.schema["max-len"] || LUPAPISTE.config.inputMaxLength};

  self.dispose = function() {
    // save on dispose as subscription is not triggered if component is killed quicker than timeout
    if (subscription) { subscription.dispose(); }
    hub.send("accordionService::saveIdentifier", {docId: self.documentId, key: self.identifierObject.key, value: self.identifier()});
  };

};
