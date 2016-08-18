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

  function buildAttachmentModel(attachment, attachmentObs) {
    var notNeeded;
    if (ko.isObservable(attachmentObs)) {
      notNeeded = attachmentObs().notNeeded;
      notNeeded(attachment.notNeeded);
      attachmentObs(_.assign(attachment, {notNeeded: notNeeded}));
      return attachmentObs;
    } else {
      notNeeded = ko.observable(attachment.notNeeded);
      notNeeded.subscribe(_.partial(self.setNotNeeded, attachment.id));
      attachmentObs = ko.observable(_.assign(attachment, {"notNeeded": notNeeded}));
      return attachmentObs;
    }
  }

  function assignAuthModel(attachment) {
    var application = lupapisteApp.models.application._js,
        authModel = authorization.create();
    authModel.refresh(application,
                      {"attachmentId": util.getIn(attachment, ["id"]),
                       "fileId": util.getIn(attachment, ["latestVersion", "fileId"])});
    return _.assign(attachment, {authModel: authModel});
  }

  self.setAttachments = function(data) {
    self.attachments(_.map(data, _.flow([assignAuthModel, buildAttachmentModel])));
  };

  self.setAttachment = function(attachment) {
    var replaceAttachment = self.getAttachment(attachment.id);
    if (replaceAttachment) {
      _.flow([assignAuthModel, _.partialRight(buildAttachmentModel, replaceAttachment)])(attachment);
    }
  };

  self.setTagGroups = function(data) {
    self.tagGroups(data);
  };

  self.setFilters = function(data) {
    self.filters(data);
  };

  self.queryAll = function() {
    forceVisibleIds([]);
    queryData("attachments", "attachments", self.setAttachments);
    queryData("attachments-tag-groups", "tagGroups", self.setTagGroups);
    queryData("attachments-filters", "attachmentsFilters", self.setFilters);
  };

  self.queryOne = function(attachmentId) {
    queryData("attachment", "attachment", self.setAttachment, {"attachmentId": attachmentId});
    queryData("attachments-tag-groups", "tagGroups", self.setTagGroups);
    queryData("attachments-filters", "attachmentsFilters", self.setFilters);
  };

  self.getAttachment = function(attachmentId) {
    return _.find(self.attachments(), function(attachment) {
      return attachment.peek().id === attachmentId;
    });
  };

  self.removeAttachment = function(attachmentId) { // use as reference for new attachment remove dialog, also put track-clicks in
    var versions = ko.unwrap(self.getAttachment(attachmentId)).versions;
    var removeSuccessCallback = function() {
      self.attachments.remove(function(attachment) {
        return attachment().id === attachmentId;
      });
      hub.send("indicator-icon", {style: "positive"});
    };
    var doDelete = function() {
      ajax.command("delete-attachment", {id: self.applicationId(), attachmentId: attachmentId})
        .success(removeSuccessCallback)
        .processing(lupapisteApp.models.application.processing)
        .call();
        hub.send("track-click", {category:"Attachments", label: "", event:"deleteAttachmentFromListing"});
      return false;
    };
    hub.send("show-dialog", {ltitle: "attachment.delete.header",
                             size: "medium",
                             component: "yes-no-dialog",
                             componentParams: {ltext: _.isEmpty(versions) ? "attachment.delete.message.no-versions" : "attachment.delete.message",
                                               yesFn: doDelete}});
  };

  self.updateAttachment = function(attachmentId, commandName, params) {
    var commandParams = _.assign({"id": self.applicationId(),
                                  "attachmentId": attachmentId},
                                 params);
    ajax.command(commandName, commandParams)
      .success(function(res) {
        self.queryOne(attachmentId);
        util.showSavedIndicator(res);
      })
      .call();
  };

  // Approving and rejecting attachments
  self.approveAttachment = function(attachmentId) {
    var attachment = self.getAttachment(attachmentId);
    self.updateAttachment(attachmentId, "approve-attachment", {"fileId": util.getIn(attachment, ["latestVersion", "fileId"])});
  };

  self.rejectAttachment = function(attachmentId) {
    var attachment = self.getAttachment(attachmentId);
    self.updateAttachment(attachmentId, "reject-attachment", {"fileId": util.getIn(attachment, ["latestVersion", "fileId"])});
  };

  self.setNotNeeded = function(attachmentId, flag) {
    forceVisibleIds.push(attachmentId);
    self.updateAttachment(attachmentId, "set-attachment-not-needed", {"notNeeded": !!flag});
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
