LUPAPISTE.AutocompleteModel = function(params) {
  "use strict";

  var self = this;

  self.dataProvider = params.dataProvider;

  self.value = params.value;

  self.query = self.dataProvider.query;

  self.inputSelected = ko.observable(false);

  self.selectItem = function(item) {
    self.value(item);
    self.query(item.label);
  };
};
