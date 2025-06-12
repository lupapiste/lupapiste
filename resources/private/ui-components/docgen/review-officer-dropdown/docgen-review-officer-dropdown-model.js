// Docgen wrapper for review officer dropdown component
LUPAPISTE.DocgenReviewOfficerDropdownModel = function(params) {
  "use strict";
  var self = this;

  params.template = (params.template || params.schema.template) || "docgen-review-officer-dropdown-template";
  ko.utils.extend(self, new LUPAPISTE.DocgenInputModel(params));
};