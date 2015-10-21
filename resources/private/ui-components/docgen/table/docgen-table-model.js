LUPAPISTE.DocgenTableModel = function(params) {
  "use strict";
  var self = this;

  // Label defaults to false (not visible) in table subcomponents
  params.schema.body = _.map(params.schema.body, function(schema) {
    return _.extend(schema, {label: !!schema.label});
  });

  // inherit from DocgenGroupModel
  ko.utils.extend(self, new LUPAPISTE.DocgenGroupModel(params));

  self.columnHeaders = _.map(params.schema.body, function(schema) {
    return self.i18npath.concat(schema.name);
  });

  if (self.repeating) {
    self.columnHeaders.push('remove');
  }
  
};