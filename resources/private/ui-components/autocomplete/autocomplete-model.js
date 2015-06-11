LUPAPISTE.AutocompleteModel = function(params) {
  "use strict";

  var self = this;

  self.dataProvider = params.dataProvider;

  self.value = params.value;

  // tagging support
  self.tags = params.tags;

  self.query = self.dataProvider.query;

  self.inputSelected = ko.observable(false);
  self.showResult = ko.observable(false);
  self.selected = ko.observable("");
  self.index = ko.observable(0);
  self.selectedTags = ko.observableArray();
  self.dataSubscription = self.dataProvider.data.subscribe(function() {
    self.index(0);
  });

  self.selectInput = function() {
    self.inputSelected(true);
    self.showResult(true);
  };

  self.selectItem = function(item) {
    if (item) {
      if (self.tags) {
        console.log("add tag");
        self.selectedTags.push(item);
        self.value(self.selectedTags());
      } else {
         self.value(item);
         self.selected(item.label);
      }
      self.inputSelected(false);
      self.showResult(false);
    }
  };

  self.navigate = function(data, event) {
    var scrollToActiveItem = function(index) {
      var $container = $(event.target).siblings("ul");
      var $activeItem = $container.find("li:nth-child(" + index + ")");

      if ($activeItem.length === 0) {
        return;
      }

      var containerTop = $container.scrollTop() + $activeItem.height();
      var containerBottom = $container.scrollTop() + $container.height() - $activeItem.height();

      var itemTop = $activeItem.position().top;
      var itemBottom = $activeItem.position().top + $activeItem.height();

      if ((itemBottom > containerBottom) || (itemTop < containerTop)) {
        $container.scrollTop($container.scrollTop() + $activeItem.position().top);
      }
    };

    if (event.keyCode === 13) {
      self.selectItem(self.dataProvider.data()[self.index()]);
    }
    else if (event.keyCode === 38) {
      self.index(self.index() > 0 ? self.index() - 1 : 0);
      scrollToActiveItem(self.index());
    }
    else if (event.keyCode === 40) {
      self.index(self.index() + 1 < self.dataProvider.data().length ? self.index() + 1 : self.index());
      scrollToActiveItem(self.index());
    }
    return true;
  };

  self.changeIndex = function(index) {
    self.index(index);
  };

  self.dispose = function() {
    self.dataSubscription.dispose();
  };
};
