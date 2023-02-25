LUPAPISTE.ModalDialogModel = function () {
  "use strict";
  var self = this;

  self.windowService = lupapisteWindow;
  self.showDialog = ko.observable(false);
  self.component = ko.observable();
  self.componentParams = ko.observable();
  self.windowWidth = ko.observable(self.windowService.windowWidth()).extend({notify: "always"});
  self.windowHeight = ko.observable(self.windowService.windowHeight()).extend({notify: "always"});
  self.dialogVisible = ko.observable(false);
  self.title = ko.observable();
  self.size = ko.observable();
  self.minContentHeight = ko.observable();
  self.id = ko.observable();
  self.css = ko.pureComputed(function() {
    return [self.size(), self.component()].join(" ");
  });
  self.closeOnClick = ko.observable();

  self.showDialog.subscribe(function(show) {
    self.dialogVisible( show );
  });

  self.calculateHeight = function() {
    // Triggers the height calculations (see the computed observables
    // below). Called via descendantsComplete binding in the
    // template.
    self.windowHeight( self.windowHeight() + 1 );
  };

  self.dialogHeight = ko.pureComputed(function() {
    return self.windowHeight() - 150;
  }).extend({notify: "always"});

  self.dialogHeightPx = ko.pureComputed(function() {
    return self.dialogHeight() + "px";
  });

  self.dialogContentHeight = ko.pureComputed(function() {
    var contentHeight = ($("#modal-dialog-content-component").find(".content").height()) + 36; // add margins
    var dialogContentHeight = self.dialogHeight() - 145; // remove margins buttons and title
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

  self.cancelAndCloseDialog = function() {
    var cancelFn = _.get(self.componentParams(), "cancelFn", _.noop);
    cancelFn();
    self.closeDialog();
  };

  self.closeDialog = function() {
    var id = self.id();

    // Click handlers were left in jQuery.cache
    $("#modal-dialog-content-component").find("a").off();
    $("#modal-dialog-content-component").find("button").off();
    $("#modal-dialog-content-container").find(".mask,.close,.component").off();

    self.showDialog(false);

    // Clear references to disposed data
    self.component(null);
    self.componentParams(null);
    self.title(null);
    self.size(null);
    self.id(null);

    $("html").removeClass("no-scroll");
    hub.send("dialog-close", {id : id});
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
    self.minContentHeight( data.minContentHeight );
    self.id(data.id || data.component);
    self.closeOnClick(data.closeOnClick || false);
    self.showDialog(true);
  });

  hub.subscribe("close-dialog", function() {
    self.closeDialog();
  });
  hub.subscribe("resize-dialog", function() {
    self.windowWidth(self.windowService.windowWidth());
    self.windowHeight(self.windowService.windowHeight());
  });


};
