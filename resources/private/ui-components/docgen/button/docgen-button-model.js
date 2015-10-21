LUPAPISTE.DocgenButtonModel = function(params) {
  "use strict";

  var self = _.extend(this, params, {
    id: params.id,
    clickFn: params.clickFn || _.noop,
    label: params.label,
    icon: params.icon,
    className: params.className || 'primary',
  });
};