LUPAPISTE.ModalDialogModel = function () {
  "use strict";
  var self = this;

  self.showDialog = ko.observable(false);
  self.component = ko.observable();
  self.componentParams = ko.observable();
  self.windowWidth = ko.observable().extend({notify: "always"});
  self.windowHeight = ko.observable().extend({notify: "always"});
  self.dialogVisible = ko.observable(false);
  self.title = ko.observable();
  self.size = ko.observable();

  self.css = ko.pureComputed(function() {
    return [self.size(), self.component()].join(" ");
  });

  self.showDialog.subscribe(function(show) {
    _.delay(function(show) {
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
    var contentHeight = ($("#modal-dialog-content-component").find(".content").height()) + 32; // add margins
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
    self.submitFn()();
    self.closeDialog();
  };

  self.closeDialog = function() {
    self.showDialog(false);
    $("html").removeClass("no-scroll");
  };

  hub.subscribe("show-dialog", function(data) {
    $("html").addClass("no-scroll");
    self.component(data.component);
    var componentParams = data.componentParams ? data.componentParams : {};
    self.componentParams(componentParams);
    self.title = data.title;
    self.size(data.size ? data.size : "large");
    self.showDialog(true);
  });

  hub.subscribe("close-dialog", function() {
    self.closeDialog();
  });

  hub.subscribe("resize-dialog", function() {
    setWindowSize(win.width(), win.height());
  });

  var setWindowSize = function(width, height) {
    self.windowWidth(width);
    self.windowHeight(height);
  };

  var win = $(window);
  // set initial dialog size
  setWindowSize(win.width(), win.height());

  // listen widow change events
  win.resize(function() {
    setWindowSize(win.width(), win.height());
  });
};
