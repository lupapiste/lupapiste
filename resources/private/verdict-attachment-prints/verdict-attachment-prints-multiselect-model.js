LUPAPISTE.VerdictAttachmentsMultiselectModel = function(params) {
    "use strict";
    var self = this;

    function markableAttachment(a) {
      return a.versions && a.versions.length;
    }

    function enhanceAttachment(a) {
      a.forPrinting = ko.observable(a.forPrinting);
    }

    function mapAttachmentGroup(group) {
      group.attachments = _(group.attachments).each(enhanceAttachment).value();
      return {
        attachments: group.attachments,
        groupName: group.groupName,
        groupDesc: group.groupDesc,
        name: group.name,
        isGroupSelected: ko.computed(function() {
          return _.every(group.attachments, function(a) {
            return a.forPrinting();
          });
        })
      };
    }

    function getSelectedAttachments(files) {
      return _(files).pluck("attachments").flatten().filter(function(f) {
        return f.forPrinting();
      }).value();
    }

    function getNonSelectedAttachments(files) {
      return _(files).pluck("attachments").flatten().filter(function(f) {
        return !f.forPrinting();
      }).value();
    }

    function eachSelected(files) {
      return _(files).pluck("attachments").flatten().every(function(f) {
        return f.forPrinting();
      });
    }


    // Init
    self.application = params.application;
    self.attachments = params.attachments;
    self.filteredFiles = _(ko.mapping.toJS(self.attachments)).filter(markableAttachment).value();

    // group by post/pre verdict attachments
    var grouped = _.groupBy(self.filteredFiles, function(a) {
      return _.contains(LUPAPISTE.config.postVerdictStates, a.applicationState) ? "post" : "pre";
    });

    // group attachments by operation
    grouped.pre = attachmentUtils.getGroupByOperation(grouped.pre, true, self.application.allowedAttachmentTypes);
    grouped.post = attachmentUtils.getGroupByOperation(grouped.post, true, self.application.allowedAttachmentTypes);

    // map files for marking
    self.preFiles = ko.observableArray(_.map(grouped.pre, mapAttachmentGroup));
    self.postFiles = ko.observableArray(_.map(grouped.post, mapAttachmentGroup));

    self.selectedFiles = ko.computed(function() {
      return getSelectedAttachments(self.preFiles()).concat(getSelectedAttachments(self.postFiles()));
    });

    self.nonSelectedFiles = ko.computed(function() {
      return getNonSelectedAttachments(self.preFiles()).concat(getNonSelectedAttachments(self.postFiles()));
    });

    self.allSelected = ko.computed(function() {
      return eachSelected(self.preFiles()) && eachSelected(self.postFiles());
    });


    self.start = function() {
      var id = self.application.id();
      ajax.command("set-attachments-as-verdict-attachment", {
        id: id,
        selectedAttachmentIds: _.map(self.selectedFiles(), "id"),
        unSelectedAttachmentIds: _.map(self.nonSelectedFiles(), "id")
      })
      .success(function() {
        window.location.hash = "!/application/" + id + "/attachments";
        repository.load(id);
        return false;
      })
      .error(function(e) {
        notify.error(loc("error.dialog.title"), loc("attachment.set-attachments-as-verdict-attachment.error"));
        repository.load(id);
      })
      .call();
      return false;
    };


    self.selectRow = function(row) {
        row.forPrinting(!row.forPrinting());
    };


    function selectAllFiles(value) {
        _(self.preFiles()).pluck("attachments").flatten().each(function(f) { f.forPrinting(value); }).value();
        _(self.postFiles()).pluck("attachments").flatten().each(function(f) { f.forPrinting(value); }).value();
    }

    self.selectAll = _.partial(selectAllFiles, true);
    self.selectNone = _.partial(selectAllFiles, false);

    self.toggleGroupSelect = function(group) {
        var sel = group.isGroupSelected();
        _.each(group.attachments, function(a) {
            a.forPrinting(!sel);
        });
    };
  };





var verdictAttachmentsMarking = (function() {
  "use strict";

  var model = {
    selectingMode: ko.observable(false),
    authorization: null,
    appModel: null,
    attachments: null,

    cancelSelecting: function() {
      var id = model.appModel.id();
      model.selectingMode(false);
      model.appModel = null;
      model.attachments = null;
      model.authorization = null;

      window.location.hash="!/application/" + id + "/attachments";
      repository.load(id);
    }
  };

  function initMarking(appModel) {
    model.appModel = appModel;
    model.attachments = model.appModel.attachments();
    model.authorization = lupapisteApp.models.authModel;

    window.location.hash="!/verdict-attachments-select/" + model.appModel.id();
  }

  hub.onPageLoad("verdict-attachments-select", function() {
    if ( pageutil.subPage() ) {
      if ( !model.appModel || model.appModel.id() !== pageutil.subPage() ) {
        // refresh
        model.selectingMode(false);

        var appId = pageutil.subPage();
        repository.load(appId, null, function(application) {
          lupapisteApp.setTitle(application.title);

          model.authorization = lupapisteApp.models.authModel;
          model.appModel = lupapisteApp.models.application;

          ko.mapping.fromJS(application, {}, model.appModel);

          model.attachments = model.appModel.attachments();

          model.selectingMode(true);
        });
      } else { // appModel already initialized, show the multiselect view
        model.selectingMode(true);
        lupapisteApp.setTitle(model.appModel.title());
      }
    } else {
      error("No application ID provided for verdict attachments multiselect");
      LUPAPISTE.ModalDialog.open("#dialog-application-load-error");
    }
  });

  hub.onPageUnload("verdict-attachments-select", function() {
    model.selectingMode(false);
  });

  hub.subscribe("start-marking-verdict-attachments", function(param) {
    initMarking(param.application);
  });

  ko.components.register("verdict-attachments-multiselect-component", {
    viewModel: LUPAPISTE.VerdictAttachmentsMultiselectModel,
    template: {element: "verdict-attachments-multiselect-template"}
  });

  $(function() {
    $("#verdict-attachment-prints-multiselect-container").applyBindings(model);
  });
})();
