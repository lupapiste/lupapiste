LUPAPISTE.DocgenTableModel = function(params) {
  "use strict";
  var self = this;

  // inherit from DocgenGroupModel
  ko.utils.extend(self, new LUPAPISTE.DocgenGroupModel(params));

  self.columnHeaders = _.map(params.schema.body, function(schema) {
    return self.i18npath.concat(schema.name);
  });
  
};