LUPAPISTE.ReactDialogModel = function (params) {
  "use strict";
  var self = this;

  self.renderCleanup = null;

  var mounted = function() {
    params.contentMountedCount(params.contentMountedCount() + 1);
  };

  // renderCallback should return an cleanup fn
  self.dialogMounted = function() {
    self.renderCleanup = params.renderCallback(mounted);
  };

  // If React component was mounted inside this dialog,
  // unmount the root when closing the dialog.
  self.dispose = function() {
    if (self.renderCleanup) {
      self.renderCleanup();
    }
  };
};
