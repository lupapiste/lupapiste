LUPAPISTE.AutocompleteModel = function(params) {
  "use strict";

  var self = this;

  // self.dataProvider = params.dataProvider;

  self.value = params.selectedOptions;

  // tagging support
  self.tags = params.tags;

  self.selectedText = params.optionsText || "label";

  self.query = params.query;

  self.inputSelected = ko.observable(false);

  self.dropdownClick = ko.observable(false);

  self.dropdownVisible = ko.computed(function() {
    return self.inputSelected() || self.dropdownClick(); // works in IE when scrollbar is clicked
  });

  self.selected = ko.observable("");

  self.index = ko.observable(0);

  self.selectedTags = ko.observableArray();

  self.options = params.options;

  self.data = ko.observableArray(self.options());

  self.showCaption = ko.pureComputed(function() {
    return !self.selected() && self.selectedTags().length === 0;
  });

  self.groupedResults = ko.pureComputed(function() {
    return _.some(self.data(), function(item) {
      if (item && item.groupHeader) {
        return true;
      }
    });
  });

  // set initial value
  if (self.tags) {
    self.selectedTags = self.value;
  } else {
    self.selected = self.value;
  }

  self.subscriptions = [];

  function getCurrentItem() {
    return self.data()[self.index()];
  }

  function initIndex() {
    self.index(0);
    // skip goup header when setting initial index
    if (getCurrentItem() && getCurrentItem().groupHeader) {
      self.index(1);
    }
  }

  // set initial index
  initIndex();

  self.subscriptions.push(self.options.subscribe(function() {
    if (params.nullable) {
      self.data([null].concat(self.options()));
    } else {
      self.data(self.options());
    }
    initIndex();
  }));

  self.selectInput = function() {
    self.inputSelected(true);
  };

  self.retainFocus = function() {
    // set to true so input blur knows if ul container (scrollbar) was clicked
    self.dropdownClick(true);
  };

  self.blur = function(_, event) {
    if (self.dropdownClick()) {
      // IE hax, return focus to input when user click scrollbar
      (event.target || event.srcElement).focus(); // IE9 event.srcElement
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
    function scrollToActiveItem(index) {
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
    }

    if (event.keyCode === 13) {
      self.selectItem(getCurrentItem());
    }

    else if (event.keyCode === 38) {
      var firstItem = self.index() <= 0;
      self.index(firstItem ? 0 : self.index() - 1);

      if (getCurrentItem() && getCurrentItem().groupHeader && self.index() <= 0) {
        self.index(self.index() + 1);
      }

      // skip group header
      else if (getCurrentItem() /* is not nullable */ && getCurrentItem().groupHeader && !firstItem) {
        self.index(self.index() - 1);
      }
      scrollToActiveItem(self.index());
    }

    else if (event.keyCode === 40) {
      var lastItem = self.index() + 1 >= self.data().length;
      self.index(lastItem ? self.index() : self.index() + 1);
      // skip groupheader
      if (getCurrentItem().groupHeader && !lastItem) {
        self.index(self.index() + 1);
      }
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
