LUPAPISTE.DocgenSelectModel = function(params) {
  "use strict";
  var self = this;

  // inherit from DocgenInputModel
  ko.utils.extend(self, new LUPAPISTE.DocgenInputModel(params));

  self.valueAllowUnset = params.schema.valueAllowUnset;
  self.optionsCaption = self.valueAllowUnset ? loc([self.path.join("."), "select"]) : null;
};