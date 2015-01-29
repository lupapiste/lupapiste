LUPAPISTE.ModalDialogModel = function (params) {
  "use strict";
  var self = this;
  self.showDialog = ko.observable(false);
  self.contentName = ko.observable();
  self.contentParams = ko.observable();
  self.windowWidth = ko.observable();
  self.windowHeight = ko.observable();

  self.dialogWidth = ko.pureComputed(function() {
    return self.windowWidth() - 200;
  });

  self.dialogHeight = ko.pureComputed(function() {
    return self.windowHeight() - 200;
  });

  self.closeDialog = function() {
    self.showDialog(false);
    $("html").removeClass("no-scroll");
  };

  hub.subscribe("show-dialog", function(data) {
    $("html").addClass("no-scroll");
    self.contentName(data.contentName);
    self.contentParams(data.contentParams);
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
