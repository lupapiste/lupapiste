LUPAPISTE.AutocompleteModel = function(params) {
  "use strict";

  var self = this;

  // TODO rethink how we handle single selection in autocomplete component
  self.selectedOptions = params.selectedOptions || ko.observableArray(_.filter([ko.unwrap(params.selectedOption)]));

  var pauseUpdatingOption = ko.observable(false);

  if (params.selectedOption) {
    params.selectedOption.subscribe(function(val) {
      pauseUpdatingOption(true);
      self.selectedOptions(_.filter([val]));
      pauseUpdatingOption(false);
    });
  }

  self.selectedOptions.subscribe(function(val) {
    if (params.selectedOption && !pauseUpdatingOption()) {
      params.selectedOption(_.first(val));
    }
  });
  // end TODO


  // Parameters
  // tagging support
  self.tags = params.tags;

  self.optionsText = params.optionsText || "label";

  self.query = params.query;

  self.options = params.options;

  self.optionsCaption = params.optionsCaption || loc("choose");

  self.nullable = params.nullable;

  // Observables
  self.index = ko.observable(0);

  self.inputSelected = ko.observable(false);

  self.dropdownClick = ko.observable(false);

  // Computed
  self.data = ko.pureComputed(function() {
    if (self.nullable && !self.tags) {
      // add nullable parameter to copy  of array
      var copy = self.options().slice();
      var item = {behaviour: "clearSelected"};
      item[self.optionsText] = self.optionsCaption;
      copy.unshift(item);
      return copy;
    } else {
      return self.options();
    }
    // reset index
    initIndex();
  });

  self.dropdownVisible = ko.pureComputed(function() {
    return self.inputSelected() || self.dropdownClick(); // works in IE when scrollbar is clicked
  });

  self.showCaption = ko.pureComputed(function() {
    return _.isEmpty(self.selectedOptions());
  });

  self.groupedResults = ko.pureComputed(function() {
    return _.some(self.data(), function(item) {
      if (item && item.groupHeader) {
        return true;
      }
    });
  });

  self.showTags = ko.pureComputed(function() {
    return self.tags && !_.find(self.selectedOptions(), {behaviour: "singleSelection"});
  });

  self.selectionText = ko.pureComputed(function() {
    return self.optionsText ? util.getIn(self.selectedOptions(), [0, self.optionsText]) : self.selectedOptions()[0];
  });

  self.showSingleSelection = ko.pureComputed(function() {
    return !self.showTags();
  });

  self.subscriptions = [];

  // Helpers
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

  // view model functions
  self.selectInput = function() {
    self.inputSelected(!self.inputSelected());
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
    if (item.behaviour === "clearSelected") {
      self.selectedOptions([]);
    } else if (item.behaviour === "singleSelection") {
      self.selectedOptions([item]);
    } else {
      if (self.tags) {
        self.selectedOptions.remove(function(item) {
          return item.behaviour === "singleSelection";
        });
        self.selectedOptions.push(item);
      } else {
        self.selectedOptions([item]);
      }
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
      if (getCurrentItem() && getCurrentItem().groupHeader && !lastItem) {
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
    self.selectedOptions.remove(tag);
    self.inputSelected(false);
  };

  self.clearQuery = function() {
    self.query("");
    self.retainFocus();
  };

  self.dispose = function() {
    while(self.subscriptions.length !== 0) {
      self.subscriptions.pop().dispose();
    }
  };
};
