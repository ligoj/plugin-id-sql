/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
define(function () {
	var current = {

		configureSubscriptionParameters: function (configuration, $container) {
			current.registerIdParentGroupSelect2(configuration, $container, 'service:id:parent-group');
			current.registerIdGroupSelect2(configuration, $container, 'service:id:group');
		},

		/**
		 * Replace the default text rendering by a Select2 for ParentGroup.
		 */
		registerIdParentGroupSelect2: function (configuration, $container, id) {
			if (!current.$super('isNodeMode')($container)) {
				configuration.validators[id] = current.validateIdGroupCreateMode;
			}
			current.$super('registerXServiceSelect2')(configuration, id, 'service/id/group', '?search[value]=');
		},

		/**
		 * Replace the input by a select2 in link mode. In creation mode, disable manual edition of 'group',
		 * and add a simple text with live
		 * validation regarding existing group and syntax.
		 */
		registerIdGroupSelect2: function (configuration, $container, id) {
			var cProviders = configuration.providers['form-group'];
			var previousProvider = cProviders[id] || cProviders.standard;
			if (configuration.mode === 'create' && !current.$super('isNodeMode')($container)) {
				cProviders[id] = function (parameter, container, $input) {
					// Register a live validation of group
					var simpleGroupId = 'service:id:group-simple-name';
					configuration.validators[simpleGroupId] = current.validateIdGroupCreateMode;

					// Disable computed parameters and remove the description, since it is overridden
					var parentParameter = $.extend({}, parameter);
					parentParameter.description = null;
					var $fieldset = previousProvider(parentParameter, container, $input).parent();
					$input.attr('readonly', 'readonly');

					// Create the input corresponding to the last part of the final group name
					var $simpleInput = $('<input class="form-control" type="text" id="' + simpleGroupId + '" required autocomplete="off">');
					cProviders.standard({
						id: simpleGroupId,
						mandatory: true
					}, $fieldset, $simpleInput);
				};
			} else {
				current.$super('registerXServiceSelect2')(configuration, id, 'service/id/sql/group/');
			}
		},

		/**
		 * Live validation of SQL group, OU and parent.
		 */
		validateIdGroupCreateMode: function () {
			validationManager.reset(_('service:id:group'));
			var $input = _('service:id:group');
			var simpleName = _('service:id:group-simple-name').val();
			var organisation = _('service:id:ou').val();
			var parent = _('service:id:parent-group').val();
			var fullName = (parent ? parent + '-' : (organisation ? organisation + '-' : '')) + (simpleName || '').toLowerCase();
			$input.val(fullName).closest('.form-group').find('.form-control-feedback').remove().end().addClass('has-feedback');
			if (fullName !== current.$super('model').pkey && !fullName.startsWith(current.$super('model').pkey + '-')) {
				validationManager.addError($input, {
					rule: 'StartsWith',
					parameters: current.$super('model').pkey
				}, 'group', true);
				return false;
			}
			// Live validation to check the group does not exists
			validationManager.addMessage($input, null, [], null, 'fas fa-sync-alt fa-spin');
			$.ajax({
				dataType: 'json',
				url: REST_PATH + 'service/id/group/' + encodeURIComponent(fullName) + '/exists',
				type: 'GET',
				success: function (data) {
					if (data) {
						// Existing project
						validationManager.addError(_('service:id:group'), {
							rule: 'already-exist',
							parameters: ['service:id:group', fullName]
						}, 'group', true);
					} else {
						// Succeed, not existing project
						validationManager.addSuccess($input, [], null, true);
					}
				}
			});

			// For now return true for the immediate validation system, even if the Ajax call may fail
			return true;
		}
	};
	return current;
});
