//
// Provides services for attachments tab.
//
//

LUPAPISTE.AttachmentsService = function() {
  "use strict";
  var self = this;
  ko.options.deferUpdates = true;
  self.APPROVED = "ok";
  self.REJECTED = "requires_user_action";

  self.attachments = ko.observableArray([]);
  self.authModels = ko.observable({});
  self.tagGroups = ko.observableArray([]);

  // array of arrays of filters received from backend along with default values.
  // [[A B] [C D]] = (and (or A B) (or C D))
  self.filters = ko.observableArray([]);

  self.activeFilters = ko.pureComputed(function() {
    return _.filter(self.filtersArray(), function(f) {
      return f.filter() === true;
    });
  });

  self.filteredAttachments = ko.pureComputed(
    function() {
      return applyFilters(self.attachments(), self.activeFilters());});

  // Ids for attachments that are visible despite of active filters
  var forceVisibleIds = ko.observableArray();

  self.authModel = lupapisteApp.models.applicationAuthModel;
  self.processing = lupapisteApp.models.application.processing;
  self.applicationId = lupapisteApp.models.application.id;

  self.applicationId.subscribe(function() {
    // to avoid retaining old filter states when going to different application
    self.internedObservables = {};
  });

  self.clearData = function() {
    // to avoid showing stale data before ajax queries return, after switching views or applications
    self.attachments([]);
    forceVisibleIds([]);
    self.filters([]);
    self.tagGroups([]);
  };

  function queryData(queryName, responseJsonKey, dataSetter, params) {
    if (self.authModel.ok(queryName)) {
      var queryParams = _.assign({"id": self.applicationId}, params);
      ajax.query(queryName, queryParams)
        .success(function(data) {
          dataSetter(data[responseJsonKey]);
        })
        .onError("error.unauthorized", notify.ajaxError)
        .call();
    }
  }


  // Initialize self.authModels for attachments. Creates new authorization models or reuses previously created ones.
  function initAuthModels(attachments) {
    self.authModels(_(attachments)
                    .keyBy("id")
                    .mapValues(function(attachment, id) {
                      return self.authModels()[id] || authorization.create();
                    })
                    .value());
  }

  // Refresh all authModels at once.
  function refreshAllAuthModels() {
    authorization.refreshModelsForCategory(self.authModels(), self.applicationId(), "attachments");
  }

  // Refresh authModel for single attachment.
  // This function should NOT be used for refreshing entire set of authModels since
  // one query is produced for each authModel.
  function refreshAuthModel(attachment) {
    var authModel = self.authModels[attachment.id];
    if (authModel) {
      authModel.refresh({id: self.applicationId(),
                         attachmentId: attachment.id,
                         fileId: util.getIn(attachment, ["latestVersion", "fileId"])});
    }
  }

  // Returns authorization model for attachment from self.authModels or creates new authModel and stores it in self.authModels.
  function getAuthModel(attachment) {
    var authModel = self.authModels()[attachment.id];
    if (!authModel) {
      authModel = authorization.create();
      self.authModels(_.set(self.authModels(), attachment.id, authModel));
    }
    return authModel;
  }

  function buildAttachmentModel(attachment, attachmentObs) {
    if (ko.isObservable(attachmentObs)) {
      attachmentObs(attachmentObs().reset(attachment));
    } else {
      attachmentObs = ko.observable(new LUPAPISTE.AttachmentModel(attachment, getAuthModel(attachment)));
    }
    return attachmentObs;
  }

  self.setAttachments = function(attachments) {
    initAuthModels(attachments);
    refreshAllAuthModels();
    self.attachments(_.map(attachments, buildAttachmentModel));
  };

  self.setAttachmentData = function(attachment) {
    var attachmentObs = self.getAttachment(attachment.id);
    if (attachmentObs) {
      buildAttachmentModel(attachment, attachmentObs);
      refreshAuthModel(attachment);
    }
  };

  self.setTagGroups = function(data) {
    self.tagGroups(data);
  };

  self.setFilters = function(data) {
    self.filters(data);
  };

  self.queryAttachments = function() {
    queryData("attachments", "attachments", self.setAttachments);
  };

  self.queryAll = function() {
    forceVisibleIds([]);
    queryData("attachments", "attachments", self.setAttachments);
    queryData("attachments-tag-groups", "tagGroups", self.setTagGroups);
    queryData("attachments-filters", "attachmentsFilters", self.setFilters);
  };

  self.queryOne = function(attachmentId) {
    queryData("attachment", "attachment", self.setAttachmentData, {"attachmentId": attachmentId});
    queryData("attachments-tag-groups", "tagGroups", self.setTagGroups);
    queryData("attachments-filters", "attachmentsFilters", self.setFilters);
  };

  self.getAttachment = function(attachmentId) {
    return _.find(self.attachments(), function(attachment) {
      return attachment.peek().id === attachmentId;
    });
  };

  self.removeAttachment = function(attachmentId, options) {
    ajax.command("delete-attachment", {id: self.applicationId(), attachmentId: attachmentId})
      .success(function(res) {
        self.attachments.remove(function(attachment) {
          return attachment().id === attachmentId;
        });
        _.get(options, "onSuccess", util.showSavedIndicator)(res);
      })
      .complete(_.get(options, "onComplete", _.noop))
      .processing(self.processing)
      .call();
    return false;
  };

  self.updateAttachment = function(attachmentId, commandName, params, options) {
    var commandParams = _.assign({"id": self.applicationId(),
                                  "attachmentId": attachmentId},
                                 params);
    ajax.command(commandName, commandParams)
      .success(_.get(options, "onSuccess", util.showSavedIndicator))
      .complete(function(res) {
        self.queryOne(attachmentId);
        _.get(options, "onComplete", _.noop)(res);
      })
      .call();
  };

  // Approving and rejecting attachments
  self.approveAttachment = function(attachmentId, options) {
    var attachment = self.getAttachment(attachmentId);
    self.updateAttachment(attachmentId, "approve-attachment", {"fileId": util.getIn(attachment, ["latestVersion", "fileId"])}, options);
  };

  self.rejectAttachment = function(attachmentId, options) {
    var attachment = self.getAttachment(attachmentId);
    self.updateAttachment(attachmentId, "reject-attachment", {"fileId": util.getIn(attachment, ["latestVersion", "fileId"])}, options);
  };

  self.setNotNeeded = function(attachmentId, flag, options) {
    forceVisibleIds.push(attachmentId);
    self.updateAttachment(attachmentId, "set-attachment-not-needed", {"notNeeded": !!flag}, options);
  };

  self.createAttachmentTempaltes = function(types, options) {
    ajax.command("create-attachments", {id: self.applicationId(), attachmentTypes: types})
      .success(function(res) {
        self.queryAll();
        _.get(options, "onSuccess", util.showSavedIndicator)(res);
      })
      .complete(_.get(options, "onComplete", _.noop))
      .call();
  };

  self.copyUserAttachments = function(options) {
    ajax.command("copy-user-attachments-to-application", {id: self.applicationId()})
      .success(function(res) {
        self.queryAll();
        _.get(options, "onSuccess", util.showSavedIndicator)(res);
      })
      .complete(_.get(options, "onComplete", _.noop))
      .processing(self.processing)
      .call();
  };

  self.downloadAttachments = function(attachmentIds) {
    var ids = attachmentIds || _(self.attachments()).map(ko.unwrap).map("id");
    var applicationId = self.applicationId();
    var uri = "/api/raw/download-attachments?id=" + applicationId + "&ids=" + ids.join(",") + "&lang=" + loc.getCurrentLanguage();
    window.open(uri);
  };

  //helpers for checking relevant attachment states
  self.isApproved = function(attachment) {
    return util.getIn(attachment, ["state"]) === self.APPROVED;
  };
  self.isRejected = function(attachment) {
    return util.getIn(attachment, ["state"]) === self.REJECTED;
  };
  self.isNotNeeded = function(attachment) {
    return util.getIn(attachment, ["notNeeded"]) === true;
  };

  function getUnwrappedAttachmentById(attachmentId) {
    return ko.utils.unwrapObservable(self.getAttachment(attachmentId));
  }

  // returns a function for use in computed
  // If all of the attachments are approved -> approved
  // If some of the attachments are rejected -> rejected
  // Else null
  self.attachmentsStatus = function(attachmentIds) {
    return function() {
      if (_.every(_.map(attachmentIds, getUnwrappedAttachmentById),
                  self.isApproved)) {
        return self.APPROVED;
      } else {
        return _.some(_.map(attachmentIds, getUnwrappedAttachmentById),
                      self.isRejected) ? self.REJECTED : null;
      }
    };
  };


  //
  // Filter manipulation
  //

  self.internedObservables = {};

  // keep track of filter toggles, since they are passed over to the UI, and
  // we need to keep using the same ones after tag updates

  function internFilterBoolean(key, def) {
    if (!self.internedObservables[key]) {
      var filterValue = ko.observable(def);
      self.internedObservables[key] = filterValue;
      filterValue.subscribe(function() {
        forceVisibleIds([]);
      });
    }
    return self.internedObservables[key];
  }

  // filter tag -> observable toggles, shared between service and UI, updated in UI
  self.filtersArray = ko.pureComputed( function() {
    return _.map(_.flatten(self.filters()),
                      function(filter /*, idx // unused */) {
                        return {ltext: "filter." + filter.tag,
                                tag: filter.tag,
                                filter: internFilterBoolean(filter.tag, filter["default"])};});
  });

  self.isAttachmentFiltered = function (att) {
    if (_.includes(forceVisibleIds(), att().id)) {
      return true;
    }
    return _.reduce(self.filters(), function (ok, group) {
          var group_tags = _.map(group, function(x) {return x.tag;});
          var active_tags = _.map(self.activeFilters(), function(x) {return x.tag;});
          var enabled = _.intersection(group_tags, active_tags);
          var tags = att().tags;
          return (ok && (!(_.first(enabled)) || _.first(_.intersection(enabled, tags)))); },  true);
  };

  function applyFilters(attachments /* , active // unused */) {
    return _.filter(attachments, self.isAttachmentFiltered);
  }

  ko.options.deferUpdates = false;
};
