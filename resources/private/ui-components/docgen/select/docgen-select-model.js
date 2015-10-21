LUPAPISTE.DocgenSelectModel = function(params) {
  "use strict";
  var self = this;

  // inherit from DocgenInputModel
  ko.utils.extend(self, new LUPAPISTE.DocgenInputModel(params));

  self.valueAllowUnset = _.isUndefined(params.schema.valueAllowUnset) || params.schema.valueAllowUnset;
  self.optionsCaption = self.valueAllowUnset ? loc(self.i18npath.concat("select")) : null;
  
  self.optionsTextFn = function(item) { 
    return loc(item.i18nkey || self.i18npath.concat(item.name));
  };
};