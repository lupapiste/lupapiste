LUPAPISTE.LinkPermitSelectorModel = function(params) {
  "use strict";
  var self = this;

  params.template = (params.template || params.schema.template) || "default-link-permit-selector-template";

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
      return util.getIn(self.validLinkPermits, [0, "id"]) || self.value();
    },
    write: self.value
  });

  var baseErrorMessage = self.errorMessage;
  self.errorMessage = self.disposedComputed(function() {
    return baseErrorMessage() || (self.validLinkPermits().length > 1) && loc(["error", "multiple-linked-permits-of-type"].concat(self.schema.operationsPath));
  });

  var baseInputClasses = self.inputClasses;
  self.inputClasses = self.disposedComputed(function() {
    return baseInputClasses() + (self.errorMessage() ? " warn" : "");
  });

  var baseLabelClasses = self.labelClasses;
  self.labelClasses = self.disposedComputed(function() {
    return baseLabelClasses() + (self.errorMessage() ? " warn" : "");
  });

};
