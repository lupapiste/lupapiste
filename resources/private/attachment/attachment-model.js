LUPAPISTE.AttachmentModel = function(attachmentData, authModel) {
  "use strict";
  var self = _.assign(this, attachmentData);
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var DEFAULT_VISIBILITY = _.head(LUPAPISTE.config.attachmentVisibilities);

  var service = lupapisteApp.services.attachmentsService;

  // Attachemnt data fields that are mapped as observables
  var observableFields = ["notNeeded", "contents", "scale", "size", "type", "op", "groupType"];

  self.authModel = authModel;

  self.processing = ko.observable(false);

  _.forEach(observableFields, function(field) {
    _.set(self, field, ko.observable(_.get(attachmentData, field)));
  });

  self.group = ko.observable(buildGroup(attachmentData));

  self.visibility = ko.observable(buildVisibility(attachmentData));

  self.reset = function(attachmentData) {
    _.forEach(observableFields, function(field) {
      _.get(self, field)(_.get(attachmentData, field));
    });

    self.processing(false);

    self.group(buildGroup(attachmentData));

    self.visibility(buildVisibility(attachmentData));

    return _.assign(self, _.omit(attachmentData, _.concat(observableFields)));
  };

  function buildGroup(data) {
    var group = {groupType: data.groupType, id: _.get(data, "op.id"), name: _.get(data, "op.name")};
    return _.isEmpty(_.filter(group)) ? null : group;
  }

  function buildVisibility(data) {
    return _.has(data.metadata, "nakyvyys") ? data.metadata.nakyvyys : DEFAULT_VISIBILITY;
  }

  //
  // Updates which require attachment model reload
  //

  var updateOptions = { onComplete: _.partial(service.queryOne, self.id)};

  self.disposedSubscribe(self.notNeeded, function(val) {
    self.processing(true);
    service.setNotNeeded(self.id, val, updateOptions);
  });

  // Helper string to subscribe changes in attachemnt type
  self.typeString = ko.computed(function() {
    return [self.type()["type-group"], self.type()["type-id"]].join(".");
  });

  self.disposedSubscribe(self.typeString, function(val) {
    self.processing(true);
    service.setType(self.id, val, updateOptions);
  });

  // Helper string to subscribe changes in op and groupType
  self.groupString = self.disposedComputed(function() {
    return _.filter([util.getIn(self.group(), ["groupType"]), util.getIn(self.group(), ["id"])], _.isString).join("-");
  });

  self.disposedSubscribe(self.groupString, function(val) {
    self.processing(true);
    self.op(_.omit(val, "groupType"));
    self.groupType(_.get(val, "groupType"));
    service.setMeta(self.id, {group: !_.isEmpty(val) ? self.group() : null}, updateOptions);
  });

  //
  // Updates which only reloads attachment model if failed
  //

  function lightUpdateErrorFn(response) {
    self.processing(true);
    util.showSavedIndicatorIcon(response);
    service.queryOne(self.id);
  }

  var lightUpdateOptions = { onSuccess: util.showSavedIndicatorIcon,
                             onError: lightUpdateErrorFn,
                             onComplete: _.noop };

  self.disposedSubscribe(self.contents, function(val) {
    service.setMeta(self.id, {contents: val}, lightUpdateOptions);
  });

  self.disposedSubscribe(self.scale, function(val) {
    service.setMeta(self.id, {scale: val}, lightUpdateOptions);
  });

  self.disposedSubscribe(self.size, function(val) {
    service.setMeta(self.id, {size: val}, lightUpdateOptions);
  });

  self.disposedSubscribe(self.visibility, function(val) {
    service.setVisibility(self.id, val, lightUpdateOptions);
  });

};
