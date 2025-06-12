// Docgen wrapper for allu-application-kind
LUPAPISTE.DocgenAlluApplicationKindModel = function( params ) {
  "use strict";
  var self = this;

  params.template = (params.template || params.schema.template) || "docgen-allu-application-kind-template";

  ko.utils.extend(self, new LUPAPISTE.DocgenInputModel(params));
};
