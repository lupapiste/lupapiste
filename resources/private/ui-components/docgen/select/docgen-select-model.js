LUPAPISTE.DocgenSelectModel = function(params) {
  "use strict";
  var self = this;

  params.template = (params.template || params.schema.template) || "default-docgen-select-template";

  // inherit from DocgenInputModel
  ko.utils.extend(self, new LUPAPISTE.DocgenInputModel(params));

  self.valueAllowUnset = _.isUndefined(params.schema.valueAllowUnset) || params.schema.valueAllowUnset;
  var locStr = loc.hasTerm(self.i18npath.concat("select")) ? self.i18npath.concat("select") : "selectone";
  self.optionsCaption = self.valueAllowUnset ? loc(locStr) : null;

  self.optionsTextFn = function(item) {
    return loc(item.i18nkey || self.i18npath.concat(item.name));
  };

  self.options = self.disposedPureComputed( function() {
    return _.sortBy( self.schema.body,
                     [function( item ) {
                       return item.name === _.get( self.schema, "other-key")
                         ? 1 : 0;
                     },
                      function( item ) {
                        return self.schema.sortBy === "displayname"
                          ? self.optionsTextFn( item ) : 0;
                      }]);

  });
};
