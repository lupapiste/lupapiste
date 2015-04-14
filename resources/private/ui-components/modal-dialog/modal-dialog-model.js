LUPAPISTE.ModalDialogModel = function (params) {
  "use strict";
  var self = this;
  self.showDialog = ko.observable(false);
  self.contentName = ko.observable();
  self.contentParams = ko.observable();

  self.loc = {};

  self.extraClass = ko.observable();
  self.windowWidth = ko.observable();
  self.windowHeight = ko.observable();
  self.dialogVisible = ko.observable(false);

  self.showDialog.subscribe(function(show) {
    _.delay(function(show) {
      self.dialogVisible(show);
    }, 100, show);
  });

  self.dialogWidth = ko.pureComputed(function() {
    return self.windowWidth() - 200;
  });

  self.dialogHeight = ko.pureComputed(function() {
    return self.windowHeight() - 150;
  });

  self.submitFn = ko.observable();
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
    self.contentName(data.contentName);
    self.contentParams(_.assign(data.contentParams,
      {submitFn: self.submitFn,
       submitEnabled: self.submitEnabled}));

    self.loc = data.loc;
    self.extraClass(data.extraClass);
    self.showDialog(true);
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
