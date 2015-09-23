LUPAPISTE.PropertyGroupModel = function(params) {
  "use strict";
  var self = this;

  // inherit from DocgenGroupModel
  ko.utils.extend(self, new LUPAPISTE.DocgenGroupModel(params));

  self.isMaaraala = ko.observable(false);
  self.documentId = params.documentId;

  var partitionedSchemas = _.partition(self.subSchemas, function(schema) {
    return schema.name === "maaraalaTunnus";
  });
  // maaraalaSchema: [[{name: "maaraalaTunnus", ...}], [{name: "..."}, {...}]] -> {name: "maaraalaTunnus"}
  self.maaraalaSchema = _.first(_.first(partitionedSchemas));
  self.otherSchemas = _.last(partitionedSchemas);
};
