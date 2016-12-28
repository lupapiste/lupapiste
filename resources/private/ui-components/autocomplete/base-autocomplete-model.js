/* Base autocomplete component provides UI for
 * autocomplete behaviour. Actual data filtering
 * should be done outside of this model, using 'query'
 * and 'options' parameters (see for example, autocomplete-handlers component).
 * Parameters:
 *   query (observable string): search text observable
 *   options (observable array): array of options to show
 *   optionsCaption: text so show when no selections are selected
 *   selectedOption (observable): observable for single selection
 *   selectedOptions (observableArray): observable for multi selections
 *   tags (boolean): tags mode
 *   nullable (boolean): true if selected value can be null (ie. Choose... dropdown)
 *   (lP|p)laholder: search placeholder text, either lockey (lPlaceholder) or text (placeholder)
 */
LUPAPISTE.AutocompleteBaseModel = function(params) {
  "use strict";

  var self = this;

  self.selectedOptions = params.selectedOptions || ko.pureComputed({
    read: function() { return _.filter([ko.unwrap(params.selectedOption)]); },
    write: function(values) { params.selectedOption(_.first(values)); }
  });

  self.disable = params.disable || false;

  // Parameters
  self.tags = params.tags; // tagging support

  self.optionsText = params.optionsText || "label";

  self.query = params.query;

  self.options = params.options;

  self.optionsCaption = params.optionsCaption || loc("choose");

  self.nullable = params.nullable;

  self.placeholder = params.lPlaceholder ? loc(params.lPlaceholder) : (params.placeholder || loc("application.filter.search") + "...");

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
    if (!ko.unwrap(self.disable)) {
      self.inputSelected(!self.inputSelected());
    }
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
    if  (_.isEmpty(item)) {
      error("Autocomplete: selected item was unexpectedly empty",
        {options: self.options, optionsText: self.optionsText, optionsCaption: self.optionsCaption, functionArgs: arguments});
      return;
    }
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
      var container = $(event.target).closest(".autocomplete-dropdown").find(".autocomplete-result");
      var activeItem = container.find("li:nth-child(" + index + ")");
      if (activeItem.length === 0) {
        return;
      }
      container.scrollTop(0);
      container.scrollTop(activeItem.offset().top - container.height() - activeItem.height());
    }

    if (event.keyCode === 13) {
      var currentItem = getCurrentItem();
      // Don't select group header or empty result
      if (currentItem && !currentItem.groupHeader) {
        self.selectItem(currentItem);
      }
    }

    else if (event.keyCode === 38) {
      var firstItem = self.index() <= 0;
      self.index(firstItem ? 0 : self.index() - 1);

      if (getCurrentItem() && getCurrentItem().groupHeader && self.index() <= 0) {
        self.index(self.index() + 1);
      }
      // skip group header
      else if (getCurrentItem() && getCurrentItem().groupHeader && !firstItem) {
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
    if (!self.disable) {
      self.selectedOptions.remove(tag);
      self.inputSelected(false);
    }
  };

  self.clearQuery = function() {
    self.query("");
    self.retainFocus();
  };

  self.maxHeightPx = ko.pureComputed(function() {
    var windowHeight = lupapisteWindow.windowHeight();
    if ( windowHeight ) {
      return windowHeight / 2 + "px"; // magic number 2, seems to work pretty well with different window sizes (zoom levels)
    } else {
      return 800 + "px";
    }
  });

};
