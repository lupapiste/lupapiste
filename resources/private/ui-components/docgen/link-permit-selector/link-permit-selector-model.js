LUPAPISTE.LinkPermitSelectorModel = function(params) {
  "use strict";
  var self = this;

  params.template = (params.template || params.schema.template) || "default-link-permit-selector-template";

  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  // inherit from DocgenInputModel
  ko.utils.extend(self, new LUPAPISTE.DocgenInputModel(params));

  self.documentId = params.documentId || params.schema.documentId;

  var allLinkPermits = lupapisteApp.models.application.linkPermitData;

  var validOperations = ko.observableArray();
  ajax.query("all-operations-in", {path: self.schema.operationsPath.join(".") })
    .success(function(res) {
      validOperations(res.operations);
    })
    .call();

  self.validLinkPermits = self.disposedComputed(function() {
    return _.filter(allLinkPermits(), function(lp) {
      return _.includes(validOperations(), lp.operation());
    });
  });

  self.linkedValue = self.disposedComputed({
    read: function() {
      return util.getIn(self.validLinkPermits(), [0, "id"]) || self.value();
    },
    write: self.value
  });

  self.linkPermitError = self.disposedComputed(function() {
    return self.errorMessage() || (self.validLinkPermits().length > 1) && loc(["error", "multiple-linked-permits-of-type"].concat(self.schema.operationsPath));
  });

  self.enrichedInputClasses = self.disposedComputed(function() {
    return self.inputClasses() + (self.linkPermitError() ? " warn" : "");
  });

  self.enrichedLabelClasses = self.disposedComputed(function() {
    return self.labelClasses() + (self.linkPermitError() ? " warn" : "");
  });

};
