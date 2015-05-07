LUPAPISTE.AutocompleteModel = function(params) {
  "use strict";

  var self = this;

  self.dataProvider = params.dataProvider;

  self.value = params.value;

  self.query = self.dataProvider.query;

  self.inputSelected = ko.observable(false);
  self.showResult = ko.observable(false);
  self.selected = ko.observable("");

  self.index = ko.observable(0);

  self.selectInput = function() {
    self.inputSelected(true);
    self.showResult(true);
  };

  self.selectItem = function(item) {
    self.value(item);
    self.selected(item.label);
    self.inputSelected(false);
    self.showResult(false);
  };

  self.navigate = function(data, event) {
    if (event.keyCode === 13) {
      self.selectItem(self.dataProvider.data()[self.index()]);
    }
    else if (event.keyCode === 38) {
      self.index(self.index() > 0 ? self.index() - 1 : 0);
    }
    else if (event.keyCode === 40) {
      self.index(self.index() + 1 < self.dataProvider.data().length ? self.index() + 1 : self.index());
    }
    return true;
  };

  self.changeIndex = function(index) {
    self.index(index);
  };
};
