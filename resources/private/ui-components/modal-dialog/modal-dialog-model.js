LUPAPISTE.ModalDialogModel = function () {
  "use strict";
  var self = this;
  var win = $(window);

  var setWindowSize = function(width, height) {
    self.windowWidth(width);
    self.windowHeight(height);
  };

  self.showDialog = ko.observable(false);
  self.component = ko.observable();
  self.componentParams = ko.observable();
  self.windowWidth = ko.observable().extend({notify: "always"});
  self.windowHeight = ko.observable().extend({notify: "always"});
  self.dialogVisible = ko.observable(false);
  self.title = ko.observable();
  self.size = ko.observable();
  self.id = ko.observable();
  self.css = ko.pureComputed(function() {
    return [self.size(), self.component()].join(" ");
  });
  self.closeOnClick = ko.observable();

  var timerId;

  self.showDialog.subscribe(function(show) {
    clearTimeout(timerId);
    timerId = _.delay(function(show) {
      self.dialogVisible(show);
      // wait until inner component is rendered and refresh dialog content height
      setTimeout(function() {
        self.windowHeight(self.windowHeight());
      }, 0);
    }, 100, show);
  });

  self.dialogHeight = ko.pureComputed(function() {
    return self.windowHeight() - 150;
  }).extend({notify: "always"});

  self.dialogHeightPx = ko.pureComputed(function() {
    return self.dialogHeight() + "px";
  });

  self.dialogContentHeight = ko.pureComputed(function() {
    var contentHeight = ($("#modal-dialog-content-component").find(".content").height()) + 36; // add margins
    var dialogContentHeight = self.dialogHeight() - 135; // remove margins buttons and title
    return contentHeight < dialogContentHeight ? contentHeight : dialogContentHeight;
  });

  self.dialogContentHeightPx = ko.pureComputed(function() {
    return self.dialogContentHeight() + "px";
  });

  self.dialogTop = ko.pureComputed(function() {
    var contentHeight = ($("#modal-dialog-content").height());
    return self.windowHeight() / 2 - (contentHeight + 135) / 2;
  });

  self.submitFn = ko.observable(undefined);

  self.submitEnabled = ko.observable();

  self.submitDialog = function() {
    self.closeDialog();
    self.submitFn()();
  };

  self.closeDialog = function() {
    self.showDialog(false);
    $("html").removeClass("no-scroll");
    hub.send("dialog-close", {id : self.id()});
  };

  self.componentClicked = function() {
    if (self.closeOnClick()) {
      self.closeDialog();
    }
    return true;
  };

  hub.subscribe("show-dialog", function(data) {
    $("html").addClass("no-scroll");
    self.component(data.component);
    self.componentParams(data.componentParams || {});
    self.title(data.ltitle ? loc(data.ltitle) : data.title);
    self.size(data.size ? data.size : "large");
    self.id(data.id || data.component);
    self.closeOnClick(data.closeOnClick || false);
    self.showDialog(true);
  });

  hub.subscribe("close-dialog", function() {
    self.closeDialog();
  });

  hub.subscribe("resize-dialog", function() {
    setWindowSize(win.width(), win.height());
  });

  // set initial dialog size
  setWindowSize(win.width(), win.height());

  // listen widow change events
  win.resize(function() {
    setWindowSize(win.width(), win.height());
  });
};
