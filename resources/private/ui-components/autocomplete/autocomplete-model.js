LUPAPISTE.AutocompleteModel = function(params) {
  "use strict";

  var self = this;

  self.dataProvider = params.dataProvider;

  self.value = params.value;

  // tagging support
  self.tags = params.tags;
  self.group = params.groupDataProvider;

  self.selectedText = ko.observable(params.selectedText);

  self.query = self.dataProvider.query;

  self.inputSelected = ko.observable(false);
  self.dropdownClick = ko.observable(false);

  self.dropdownVisible = ko.computed(function() {
    return self.inputSelected() || self.dropdownClick(); // works in IE when scrollbar is clicked
  });

  self.selected = ko.observable("");
  self.index = ko.observable(0);
  self.selectedTags = ko.observableArray();

  self.data = ko.observableArray(self.dataProvider.data());

  self.showCaption = ko.pureComputed(function() {
    return !self.selected() && self.selectedTags().length === 0;
  });

  // set initial value
  if (self.tags) {
    self.selectedTags = self.value;
  } else {
    self.selected = self.value;
  }

  self.subscriptions = [];

  self.subscriptions.push(self.dataProvider.data.subscribe(function() {
    if (params.nullable) {
      self.data([null].concat(self.dataProvider.data()));
    } else {
      self.data(self.dataProvider.data());
    }
    self.index(0);
  }));

  self.selectInput = function() {
    self.inputSelected(true);
  };
  self.retainFocus = function() {
    // set to true so input blur knows if ul container (scrollbar) was clicked
    self.dropdownClick(true);
  };
  self.blur = function() {
    if (self.dropdownClick()) {
      // IE hax, return focus to input when user click scrollbar
      $(event.target || event.srcElement).focus(); // IE9 event.srcElement
      self.dropdownClick(false);
      return false;
    } else {
      return true;
    }
  };

  self.selectItem = function(item) {
    if (self.tags) {
      self.value.push(item);
    } else {
      self.value(item);
    }
    self.dropdownClick(false); // set to false so dropdown closes
    self.inputSelected(false);
    self.query("");
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
      self.selectItem(self.data()[self.index()]);
    }
    else if (event.keyCode === 38) {
      self.index(self.index() > 0 ? self.index() - 1 : 0);
      scrollToActiveItem(self.index());
    }
    else if (event.keyCode === 40) {
      self.index(self.index() + 1 < self.data().length ? self.index() + 1 : self.index());
      scrollToActiveItem(self.index());
    }
    return true;
  };

  self.changeIndex = function(index) {
    self.index(index);
  };

  self.removeTag = function(tag) {
    self.selectedTags.remove(tag);
    self.inputSelected(false);
  };

  self.dispose = function() {
    while(self.subscriptions.length !== 0) {
      self.subscriptions.pop().dispose();
    }
  };
};
