import gym

from RL_engine import DQN

DISPLAY_REWARD_THRESHOLD = 400  # renders environment if total episode reward is greater then this threshold
RENDER = False  # rendering wastes time

env = gym.make('CartPole-v0')
env.seed(1)  # reproducible, general Policy gradient has high variance
env = env.unwrapped

print(env.action_space)
print(env.observation_space)
print(env.observation_space.high)
print(env.observation_space.low)

'''
This is just a demo showing how DQN should be written.
NOTE THAT This DQN will not work for CartPole because plain DQN takes a lot of time training,
and this can hardly meet the requirement of real time feedback.
'''
RL = DQN(
    n_actions=env.action_space.n,
    n_features=env.observation_space.shape[0],
    learning_rate=0.2,
    epsilon=0.9,
    gamma=0.9,
    batch_size=50,
    memory_size=500,
    # output_graph=True,
)

step = 0
for i_episode in range(30000):
    observation = env.reset()
    running_reward = float('-inf')
    while True:
        if True:
            env.render()
        action = RL.choose_action(observation)
        observation_, reward, done, info = env.step(action)
        RL.store_transition(observation, action, reward, observation_)
        observation = observation_
        if step > 50:
            step += 1
        RL.do_learning()
        if done:
            ep_rs_sum = sum(RL.reward_sum)

            if running_reward == float('-inf'):
                running_reward = ep_rs_sum
            else:
                running_reward = running_reward * 0.99 + ep_rs_sum * 0.01
            if running_reward > DISPLAY_REWARD_THRESHOLD:
                RENDER = True     # rendering
            print("episode:", i_episode, "  reward:", int(running_reward))
            RL.reward_sum = []
            break

RL.plot_loss()
