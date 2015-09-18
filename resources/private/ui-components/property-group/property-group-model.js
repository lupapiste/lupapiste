LUPAPISTE.PropertyGroupModel = function(params) {
  "use strict";
  var self = this;

  console.log(params);

  // inherit from DocgenGroupModel
  ko.utils.extend(self, new LUPAPISTE.DocgenGroupModel(params));

  self.isMaaraala = ko.observable(false);

  var partitionedSchemas = _.partition(self.subSchemas, function(schema) {
    return schema.name === "maaraalaTunnus";
  });
  // maaraalaSchema: [[{name: "maaraalaTunnus", ...}], [{name: "..."}, {...}]] -> {name: "maaraalaTunnus"}
  self.maaraalaSchema = _.first(_.first(partitionedSchemas));
  self.otherSchemas = _.last(partitionedSchemas);

  console.log(self.maaraalaSchema);
    
  // remove maaraala component
  // self.maaraalaSchema = self.subSchemas[0];
  // self.maaraalaModel = params.model.maaraalaTunnus;
  // self.subschemas = self.subSchemas.slice(1);

  // console.log(params.model);
  // var modelData = _.map(params.model, function(model, key) {
  //   var obj = {};
  //   obj[key] = model.value;
  //   return obj;
  // });
  // console.log(modelData);
};
