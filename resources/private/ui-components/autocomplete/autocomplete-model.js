LUPAPISTE.AutocompleteModel = function(params) {
  "use strict";

  var self = this;

  self.dataProvider = params.dataProvider;

  self.value = params.value;

  self.query = self.dataProvider.query;

  self.inputSelected = ko.observable(false);
  self.showResult = ko.observable(false);
  self.selected = ko.observable("");

  self.selectInput = function() {
    self.inputSelected(true);
    self.showResult(true);
  };

  self.selectItem = function(item) {
    console.log("selectItem", item);
    self.value(item);
    self.selected(item.label);
    self.showResult(false);
  };
};
